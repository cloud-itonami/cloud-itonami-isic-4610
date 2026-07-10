# Governance

`cloud-itonami-isic-4610` is an OSS open-business blueprint for a commission
broker/trade intermediary -- a firm engaged by one or both principals to
arrange the sale or purchase of goods for a fee or commission, without ever
taking ownership/title of the goods itself (ISIC 4610, "wholesale on a fee or
contract basis").

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a brokerage-deal whose jurisdiction has no official commission-brokerage /
  sanctions spec-basis can never be verified, confirmed or invoiced.
- the Commission Broker Governor remains independent of the advisor.
- hard governor violations (a fabricated spec-basis, incomplete
  counterparty-diligence evidence, no mandate/engagement-letter on file, an
  unverified principal's identity, an undisclosed dual-agency conflict of
  interest, an unresolved sanctions-screening flag on either principal, a
  double deal-confirmation or a double commission-invoice) cannot be
  overridden by human approval.
- every intake, verification, confirmation, settlement and hold is auditable.
- principal, mandate, credit, KYC and sanctions data stays outside Git.
- the broker never takes ownership/title of the goods it arranges deals
  for -- code and docs must not blur this into a principal-trading act.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:
- bypassing deal-confirmation or commission-invoice policy checks
- mishandling principal, mandate, credit or sanctions-screening data
- misrepresenting certification status
- failing to respond to security incidents
