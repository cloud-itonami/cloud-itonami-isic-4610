(ns commtrade.facts
  "Per-jurisdiction commission-brokerage / commercial-agency regulatory
  catalog -- the G2-style spec-basis table the Commission Broker
  Governor checks every `:mandate/verify` proposal against ('did the
  advisor cite an OFFICIAL public source for this jurisdiction's
  commission-brokerage / sanctions requirements, or did it invent
  one?').

  A commission broker (ISIC 4610, 'wholesale on a fee or contract
  basis') NEVER takes ownership/title of the goods it arranges the
  sale or purchase of -- unlike the fuel-wholesale sibling
  (`cloud-itonami-isic-4671`, ISIC 4671) or the general-trading sibling
  (`cloud-itonami-isic-4690`, ISIC 4690), both of which take PRINCIPAL
  positions (buy then resell, invoice settlement is their own money).
  A commission broker is engaged by one or both principals to arrange
  a deal for a fee/commission; the actual sale settles directly
  between the two principals, and the broker's own actuation is
  arranging/confirming the deal and invoicing its OWN commission, not
  settling the underlying trade. Its defining regulatory exposure is
  therefore neither a single-commodity excise (the fuel-wholesale
  sibling) nor cross-border export control (the general-trading
  sibling) -- it is COMMERCIAL-AGENCY / BROKERAGE LAW: who may lawfully
  act as an intermediary for a fee, what fiduciary duties that
  intermediary owes each principal, and -- the check with no analog in
  either principal-trading sibling -- whether acting for BOTH sides of
  the same deal (dual agency) has been disclosed and consented to.

  Each entry below is a REAL jurisdiction with a REAL commission-
  brokerage / commercial-agency regime, verified against primary or
  authoritative secondary sources before being seeded here (never
  fabricated, per this fleet's honest-coverage discipline):

  - Japan: 商法 (Commercial Code, Act No. 48 of 1899) 仲立営業
    (brokerage business) 543条-550条 -- Article 543 defines 仲立人
    (nakadachinin) as '他人間の商行為の媒介をすることを業とする者'
    ('one who, as a business, mediates commercial transactions between
    others') -- the direct statutory definition of a commission
    broker/intermediary who arranges deals for a fee without becoming
    a party to them. Economic-sanctions payment/capital-transaction
    restrictions are separately administered under 外国為替及び外国
    貿易法 (FEFTA) by MOF's International Bureau.
  - United Kingdom: The Commercial Agents (Council Directive)
    Regulations 1993 (SI 1993/3053), implementing EU Council Directive
    86/653/EEC -- governs a self-employed intermediary with continuing
    authority to negotiate (or negotiate and conclude, in the
    principal's name) the sale or purchase of goods on behalf of a
    principal. Preserved as UK assimilated law post-Brexit (still in
    force).
  - Germany: Handelsgesetzbuch (HGB) §§84-92c (Handelsvertreter --
    commercial agent), also implementing Directive 86/653/EEC -- a
    Handelsvertreter mediates or concludes business for another
    entrepreneur as a self-employed trader, the same commission-agency
    shape.
  - United States: the Perishable Agricultural Commodities Act (PACA),
    7 U.S.C. §499a et seq., administered by USDA's Agricultural
    Marketing Service (AMS) -- federally LICENSES 'commission
    merchants, dealers, and brokers' of perishable agricultural
    commodities by name; PACA's own 'broker' definition ('a person
    engaged in negotiating sales and purchases ... either for or on
    behalf of the seller or buyer', 7 U.S.C. §499a(b)(7)) is close to
    an exact match for ISIC 4610. This is scoped honestly: PACA covers
    only ONE commodity category (perishable agricultural commodities),
    not a general federal licence for every US commission-agent
    wholesale trade -- see `coverage`'s note and
    `docs/business-model.md`'s Jurisdiction coverage section.
  - South Africa: the Agricultural Produce Agents Act 12 of 1992 (as
    amended by the Agricultural Produce Agents Amendment Act 47 of
    2003), administered by the Agricultural Produce Agents Council
    (APAC) under the Minister of Agriculture -- s1(1)(i) defines
    'agent' as 'a person who, for the acquisition of gain on his own
    account or in a partnership, in any manner holds himself out as a
    person who, or directly or indirectly advertises that he, on the
    instructions of or on behalf of any other person, purchases or
    sells agricultural produce or negotiates in connection therewith
    or canvasses or undertakes or offers to canvass a purchaser or
    seller therefor' -- the same commission-agent shape as ISIC 4610
    (fee-for-service intermediation, no title to the goods). s16(1)(a)
    then LICENSES this activity: 'no person shall act as an agent
    unless he is the holder of a valid fidelity fund certificate'
    issued by the council. Scoped honestly, the same discipline as
    the USA/PACA entry above: Schedule 1 limits 'agricultural
    produce' to fresh-produce-agent goods (flowers, ornamental/pot
    plants, vegetables, fruit, culinary herbs) and livestock-agent
    goods (livestock, meat, by-products, cured hides/skins) -- a
    real, verifiable, currently-administered licensing regime (APAC
    published new Rules in respect of Export Agents in 2025, Board
    Notice 915 of 2026) for exactly this business model, but for
    agricultural produce specifically, not a general commercial-
    agency statute covering every South African commission-based
    wholesale trade -- see `coverage`'s note.

  The required-evidence set (mandate/engagement-letter record, BOTH
  principals' KYC records, sanctions-screening record covering BOTH
  principals, and a dual-agency disclosure/consent record) mirrors the
  counterparty-diligence + fiduciary-conflict evidence a commission-
  brokerage compliance function actually demands before a deal is
  confirmed and a commission invoice is settled.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the counterparty-
  diligence + dual-agency evidence set (mandate/engagement-letter
  record, buyer-principal KYC record, seller-principal KYC record,
  sanctions-screening record covering both principals, dual-agency
  disclosure/consent record); `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  `:mandate/verify` proposal can commit."
  {"JPN" {:name "JPN"
          :owner-authority "法務省 (Ministry of Justice, 商法所管) / 財務省 (MOF) 国際局 (経済制裁の所管)"
          :legal-basis "商法 (Commercial Code, Act No. 48 of 1899) 仲立営業に関する規定 (商法543条: 他人間の商行為の媒介をすることを業とする者=仲立人; 543条〜550条); 外国為替及び外国貿易法 (FEFTA) に基づく経済制裁 (支払等規制)"
          :provenance "https://laws.e-gov.go.jp/law/132AC0000000048"
          :required-evidence ["mandate/engagement-letter (commission-terms) record"
                               "buyer-principal KYC record"
                               "seller-principal KYC record"
                               "sanctions-screening (OFAC/equivalent) record covering both principals"
                               "dual-agency disclosure/consent record"]}
   "GBR" {:name "GBR"
          :owner-authority "Department for Business and Trade (policy successor to the Department of Trade and Industry, which made SI 1993/3053) / Office of Financial Sanctions Implementation (OFSI), HM Treasury"
          :legal-basis "The Commercial Agents (Council Directive) Regulations 1993 (SI 1993/3053, implementing EU Council Directive 86/653/EEC); UK financial sanctions regulations"
          :provenance "https://www.legislation.gov.uk/uksi/1993/3053/contents"
          :required-evidence ["mandate/engagement-letter (commission-terms) record"
                               "buyer-principal KYC record"
                               "seller-principal KYC record"
                               "sanctions-screening (OFAC/equivalent) record covering both principals"
                               "dual-agency disclosure/consent record"]}
   "DEU" {:name "DEU"
          :owner-authority "Bundesministerium der Justiz (Federal Ministry of Justice, HGB) / Deutsche Bundesbank (financial-sanctions administration)"
          :legal-basis "Handelsgesetzbuch (HGB) §§84-92c (Handelsvertreter, implementing EU Council Directive 86/653/EEC); EU financial sanctions regulations, administered in Germany by the Deutsche Bundesbank"
          :provenance "https://www.gesetze-im-internet.de/hgb/__84.html"
          :required-evidence ["mandate/engagement-letter (commission-terms) record"
                               "buyer-principal KYC record"
                               "seller-principal KYC record"
                               "sanctions-screening (OFAC/equivalent) record covering both principals"
                               "dual-agency disclosure/consent record"]}
   "USA" {:name "USA"
          :owner-authority "USDA Agricultural Marketing Service (AMS) / OFAC (U.S. Treasury)"
          :legal-basis "Perishable Agricultural Commodities Act (PACA), 7 U.S.C. §499a et seq. (federal commission-merchant/broker licensing -- scoped to perishable agricultural commodities, see coverage note); OFAC sanctions programs (31 C.F.R. Chapter V)"
          :provenance "https://www.ams.usda.gov/rules-regulations/paca"
          :required-evidence ["mandate/engagement-letter (commission-terms) record"
                               "buyer-principal KYC record"
                               "seller-principal KYC record"
                               "sanctions-screening (OFAC/equivalent) record covering both principals"
                               "dual-agency disclosure/consent record"]}
   "ZAF" {:name "ZAF"
          :owner-authority "Agricultural Produce Agents Council (APAC), a juristic person established by the Act, under the Minister of Agriculture"
          :legal-basis "Agricultural Produce Agents Act 12 of 1992, as amended by the Agricultural Produce Agents Amendment Act 47 of 2003 (federal-equivalent national commission-agent licensing -- scoped to agricultural produce, see coverage note): s1(1)(i) defines 'agent' as a person who, for gain, on the instructions of or on behalf of any other person, purchases or sells agricultural produce or negotiates/canvasses in connection therewith; s16(1)(a) prohibits any person from acting as an agent unless the holder of a valid fidelity fund certificate issued by the council"
          :provenance "https://www.gov.za/documents/agricultural-produce-agents-act"
          :required-evidence ["mandate/engagement-letter (commission-terms) record"
                               "buyer-principal KYC record"
                               "seller-principal KYC record"
                               "sanctions-screening (OFAC/equivalent) record covering both principals"
                               "dual-agency disclosure/consent record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to confirm a deal
  or invoice a commission on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4610 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `commtrade.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements. NOTE: "
                 "the USA entry is honestly scoped to PACA-licensed "
                 "commission merchants/brokers of PERISHABLE "
                 "AGRICULTURAL COMMODITIES -- a real, verifiable federal "
                 "licensing regime for exactly this business model, but "
                 "for one commodity category, not a claim that all US "
                 "commission-agent wholesale trade is federally "
                 "licensed this way (a genuine, honestly-flagged "
                 "coverage gap). Likewise the ZAF entry is honestly "
                 "scoped to APAC-licensed agents dealing in the "
                 "agricultural produce listed in the Agricultural "
                 "Produce Agents Act's Schedule 1 (fresh produce and "
                 "livestock/meat/hides), not a general South African "
                 "commercial-agency statute covering every "
                 "commission-based wholesale trade (another honestly-"
                 "flagged coverage gap).")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
