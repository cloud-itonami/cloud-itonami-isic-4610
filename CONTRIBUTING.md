# Contributing

`cloud-itonami-isic-4610` accepts contributions to the OSS blueprint, the
Commission Broker Governor, decision-rule tests, documentation and operator
model.

## Development
The capability layer is SELF-CONTAINED. There is no pre-existing bespoke
commission-brokerage capability library to wrap; the mandate-on-file /
principal-identity / dual-agency-disclosure / sanctions-screening checks live
directly in `commtrade.governor`. This repo holds the business blueprint, the
langgraph-clj actor and the operator contracts.

```bash
clojure -M:dev:test
clojure -M:lint
```

## Rules
- Do not commit real principal, mandate, credit, KYC or sanctions-screening
  data.
- Keep deal confirmation and commission-invoice settlement behind the
  Commission Broker Governor.
- Treat commission-brokerage workflows as high-risk: add tests for
  spec-basis, evidence completeness, mandate-on-file, principal-identity
  verification, dual-agency disclosure, sanctions screening and audit
  logging.
- Never fabricate a jurisdiction's commission-brokerage or sanctions
  requirements in `commtrade.facts` -- cite a real official source or leave
  the jurisdiction out of the catalog.
- Never let `:deal/confirm` imply the broker takes title to, or moves,
  the underlying goods or money between the two principals -- that
  distinction (agency vs. principal trading) is this vertical's whole
  reason to exist; do not blur it in new code or docs.

## Pull Requests
PRs should describe: what behavior changed, which governor invariant is
affected, how it was tested, whether operator or certification docs need
updates.
