(ns ashiba.seal
  "Port of jp-ashiba py/kotoba_seal.py (iter-59) attribute-tier classification +
   sealing envelope helpers.

   Source: 20-actors/jp-ashiba/py/kotoba_seal.py (MIGRATION-rw-to-kotoba-sovereign.md
   Step 4 per-app SecureVault wiring). Each :ashiba/* attribute is classified into
   one of three tiers:
     - TIER_PUBLIC : CID-addressable plaintext is OK
     - TIER_2      : sealed with AES-256-GCM (kotoba-py SecureVault), keyed by actor DID
     - TIER_3      : signal:v1: envelope, keyed per actor DID + run_graph

   Parity note: `is-sealed?` / `expects-seal?` / `validate-tx-payload!` / `self-check`
   / the tier sets and `attr-tier` map are golden-tested byte-for-byte against
   `uv run --no-project python3 -c \"import kotoba_seal ...\"` output (2026-07-07).

   `seal!`/`unseal` are a NEW capability, not a literal port: the python source never
   implements the crypto (`seal`/`unseal` both `raise NotImplementedError(\"wired at
   runtime\")` unconditionally — there is no python golden to match). This namespace
   provides a real AES-256-GCM implementation on the JVM (`javax.crypto`, following the
   `sha256-hex` JVM-crypto convention in `orgs/gftdcojp/ai-gftd-arms/clj/src/arms/domain.cljc`)
   so the tier-2/tier-3 envelope format is genuinely exercisable. The key derivation here
   (SHA-256 of actor-did [+ run-graph for tier-3]) is a bridging placeholder — it is NOT
   the real kotoba-py SecureVault key management (no HSM / no per-actor persisted key
   material). Wiring to the real actor vault key is a follow-up (see CLAUDE.md)."
  (:require [clojure.string :as str])
  #?(:clj (:import [java.security MessageDigest SecureRandom]
                    [java.util Base64]
                    [javax.crypto Cipher]
                    [javax.crypto.spec GCMParameterSpec SecretKeySpec])))

;; ─── Attribute classification (iter-58 migration plan §Step 4) ─────────────

(def tier-public "public")
(def tier-2 "tier2")
(def tier-3 "tier3")

;; CID-addressable plaintext (14 attrs)
(def public-attrs
  #{":ashiba/site-id"
    ":ashiba/epoch"
    ":ashiba/confidence"
    ":ashiba/match-rate"
    ":ashiba/accident-risk"
    ":ashiba/safety-decision"
    ":ashiba/sent-at"
    ":ashiba/detected-by"
    ":ashiba/resolved-by"
    ":ashiba/sent-by"
    ":ashiba/predicted-by"
    ":ashiba/segmentation-cid"
    ":ashiba/image-cid"
    ":ashiba/tile-coords"})

;; Tier-2: SecureVault AES-256-GCM (4 attrs)
(def tier-2-attrs
  #{":ashiba/parcel-id"       ; 国土地理院 地番 — narrowed to property
    ":ashiba/registry"        ; 法務局 所有者 DID
    ":ashiba/permit-type"     ; 建築確認申請 区域
    ":ashiba/risk-factors"})  ; IoT 異常タグ

;; Tier-3: signal:v1: envelope, per actor DID + run_graph (3 attrs)
(def tier-3-attrs
  #{":ashiba/proposal-text"        ; full LLM-generated proposal (PII)
    ":ashiba/vendor-candidates"    ; comma-separated DIDs (commercial sensitive)
    ":ashiba/mailer-message-id"})  ; ties to outbound message + reply chain

(def attr-tier
  (merge (zipmap public-attrs (repeat tier-public))
         (zipmap tier-2-attrs (repeat tier-2))
         (zipmap tier-3-attrs (repeat tier-3))))

(defn is-sealed?
  "A string value is sealed iff it starts with `signal:v1:`."
  [value]
  (and (string? value) (str/starts-with? value "signal:v1:")))

(defn expects-seal?
  "Tier-2 and Tier-3 attrs must arrive at transact as signal:v1:..."
  [attr]
  (contains? #{tier-2 tier-3} (get attr-tier attr)))

(defn validate-tx-payload!
  "Throw ex-info if a tier-2/tier-3 attr is being written as plaintext.
   Mirrors py `validate_tx_payload` (raises ValueError there)."
  [attr value]
  (when (expects-seal? attr)
    (when-not (and (string? value) (is-sealed? value))
      (throw (ex-info
              (str "SECURITY: " attr " is " (get attr-tier attr)
                   " but value is not signal:v1: sealed ("
                   #?(:clj (.getName (class value)) :cljs (type value))
                   "). Call seal() first.")
              {:attr attr :value value :tier (get attr-tier attr)})))))

(defn self-check
  "Return [public-count tier2-count tier3-count]. Used by tests / smoke checks."
  []
  [(count public-attrs) (count tier-2-attrs) (count tier-3-attrs)])

;; ─── Real AES-256-GCM envelope (JVM only) ──────────────────────────────────
;; NOT a python port (python never implements this) — see namespace docstring.

#?(:clj
   (defn- sha256-bytes ^bytes [^String s]
     (.digest (MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))))

#?(:clj
   (defn- derive-key
     "Bridging key derivation: SHA-256(actor-did [':' run-graph]) as AES-256 key
      material. NOT the real kotoba-py SecureVault key — placeholder pending
      actor-vault-key wiring (see namespace docstring)."
     ^SecretKeySpec [actor-did run-graph]
     (let [material (if run-graph (str actor-did ":" run-graph) actor-did)]
       (SecretKeySpec. (sha256-bytes material) "AES"))))

#?(:clj
   (defn seal!
     "Encrypt `plaintext-bytes` with a key derived from `actor-did` (and
      `run-graph` for tier-3 context binding) and return a `signal:v1:<b64>`
      string. `tier` must be `tier-2` or `tier-3` (public attrs are never
      sealed — mirrors py `expects_seal`)."
     (^String [actor-did plaintext-bytes tier] (seal! actor-did nil plaintext-bytes tier))
     (^String [actor-did run-graph plaintext-bytes tier]
      (when (= tier tier-public)
        (throw (ex-info "cannot seal a TIER_PUBLIC attribute" {:tier tier})))
      (let [key (derive-key actor-did (when (= tier tier-3) run-graph))
            iv (byte-array 12)
            _ (.nextBytes (SecureRandom.) iv)
            cipher (Cipher/getInstance "AES/GCM/NoPadding")
            _ (.init cipher Cipher/ENCRYPT_MODE key (GCMParameterSpec. 128 iv))
            ciphertext (.doFinal cipher plaintext-bytes)
            envelope (byte-array (+ (count iv) (count ciphertext)))]
        (System/arraycopy iv 0 envelope 0 (count iv))
        (System/arraycopy ciphertext 0 envelope (count iv) (count ciphertext))
        (str "signal:v1:" (.encodeToString (Base64/getEncoder) envelope))))))

#?(:clj
   (defn unseal
     "Reverse of seal! — for use by downstream actors via Datalog q() pull."
     (^bytes [actor-did sealed-value tier] (unseal actor-did nil sealed-value tier))
     (^bytes [actor-did run-graph sealed-value tier]
      (when-not (is-sealed? sealed-value)
        (throw (ex-info "unseal expects signal:v1: prefix"
                         {:got (subs sealed-value 0 (min 32 (count sealed-value)))})))
      (let [key (derive-key actor-did (when (= tier tier-3) run-graph))
            envelope (.decode (Base64/getDecoder) (subs sealed-value (count "signal:v1:")))
            iv (java.util.Arrays/copyOfRange envelope 0 12)
            ciphertext (java.util.Arrays/copyOfRange envelope 12 (count envelope))
            cipher (Cipher/getInstance "AES/GCM/NoPadding")]
        (.init cipher Cipher/DECRYPT_MODE key (GCMParameterSpec. 128 iv))
        (.doFinal cipher ciphertext)))))
