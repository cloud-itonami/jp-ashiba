# jp-ashiba CLJ runtime

This directory replaces the old `lg_ashiba` Python BMC helper scaffold.

Implemented handlers:

- `health`
- `load_bmc`
- `score_bmc`
- `run_bmc`

The runtime parses `docs/bmc/ashiba-lean-bmc-v<N>.toml`, builds the same
deterministic BMC datoms, computes block maturity, lists unvalidated
hypotheses, and renders a score report. Kotoba writes are represented as a
local ingest summary so the task remains portable.

Run tests:

```sh
clojure -M:test
```

Run server:

```sh
LANGSERVER_PORT=8080 clojure -M -m ashiba.server
```
