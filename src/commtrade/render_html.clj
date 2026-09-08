(ns commtrade.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: it previously had no
  demo page and no generator at all. EVERY id, number, disposition and
  hold reason on the generated page is produced by ACTUALLY EXECUTING
  this repo's own actor stack at build time --
  `commtrade.operation` (langgraph StateGraph) -> `commtrade.governor`
  (Commission Broker Governor) -> `commtrade.phase` (rollout gate) ->
  `commtrade.store` (MemStore SSoT + append-only ledger) -- against the
  real seeded brokerage-deals `bd-1`..`bd-6` from
  `commtrade.store/demo-data`. Nothing on the page is hand-typed
  domain data.

  The scenario is adapted from this repo's own `commtrade.sim` demo
  driver (`clojure -M:dev:run`), which was run and read BEFORE writing
  this file to confirm its subject ids really exist in the store's seed
  (they do), and extended with one case the sim does not cover: a
  `:deal/confirm` on a brokerage-deal whose `:mandate/verify` was
  itself HARD-held, which reaches the governor's `:evidence-incomplete`
  check. All eight of this governor's HARD hold rules are exercised.

  Three page tables are derived from LIVE values rather than prose:

    - the action gate reads `commtrade.phase/phases`,
      `commtrade.phase/write-ops` and `commtrade.governor/high-stakes`
      directly, calls `commtrade.phase/gate` for the phase layer's own
      contribution, and shows alongside them the dispositions ACTUALLY
      observed for that op in this run -- so a disagreement between the
      documented gate and the real actor would show up on the page
      instead of being hidden by hand-written prose;
    - the HARD-hold table is read off the ledger's `:violations`, rule
      name and Japanese detail string verbatim;
    - the jurisdiction coverage table is `commtrade.facts/coverage`'s
      own honest output, including its coverage-gap note.

  DETERMINISTIC: no timestamps, no randomness, no wall-clock. Two
  consecutive runs against the same seed are byte-identical (verify by
  diffing them).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [commtrade.facts :as facts]
            [commtrade.governor :as governor]
            [commtrade.operation :as op]
            [commtrade.phase :as phase]
            [commtrade.store :as store]
            [langgraph.graph :as g]))

(def ^:private operator
  "The human operator identity injected as the actor `:context`. Phase 3
  is `commtrade.phase/default-phase` -- the most permissive rollout
  phase this actor has, which still never auto-commits an actuation."
  {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

;; ----------------------------- scenario -----------------------------

(defn- step-fact
  "One row of the run log, read off a REAL `langgraph.graph/run*`
  result -- the graph's own `:status`, the `:decide` node's own
  `:disposition`, and the governor's own verdict."
  [label request result]
  {:label       label
   :op          (:op request)
   :subject     (:subject request)
   :status      (:status result)
   :disposition (get-in result [:state :disposition])
   :confidence  (get-in result [:state :verdict :confidence])
   :violations  (mapv :rule (get-in result [:state :verdict :violations]))})

(defn run-demo!
  "Seeds a fresh `commtrade.store/MemStore`, builds the REAL
  OperationActor and executes a scenario over the real seeded
  brokerage-deals. Returns {:store db :runs [step-fact ..]}.

  One FULL CLEAN LIFECYCLE, on `bd-1` (JPN, seller-side mandate, both
  principals KYC-cleared and sanctions-screened):
  `:mandate/intake` auto-commits (the only op in phase 3's `:auto`
  set); `:mandate/verify` is governor-clean but the PHASE gate still
  escalates it (`:phase-approval`) and a human approves; `:deal/confirm`
  and `:commission/invoice` escalate at BOTH layers (the governor's
  `high-stakes` set and the phase gate) and a human approves each, so
  bd-1 ends up confirmed AND commission-invoiced with real registry
  numbers.

  EIGHT HARD GOVERNOR HOLDS, none of which ever reaches a human -- the
  `:decide` node routes them straight to `:hold`, bypassing the
  `interrupt-before #{:request-approval}` pause entirely:

    :no-spec-basis                          bd-2 (jurisdiction \"ATL\"
                                            is deliberately absent from
                                            `commtrade.facts/catalog`)
    :evidence-incomplete                    bd-2 confirm attempted after
                                            its verify was held, so no
                                            committed assessment exists
    :mandate-missing                        bd-3 (`:mandate-terms` nil)
    :principal-identity-unverified          bd-4 (buyer KYC not cleared)
    :counterparty-sanctions-flag-unresolved bd-5 (seller not screened)
    :conflict-of-interest-undisclosed       bd-6 (`:mandate-side :dual`
                                            with no disclosed consent)
    :already-confirmed                      bd-1 confirmed twice
    :already-invoiced                       bd-1 invoiced twice"
  []
  (let [db    (store/seed-db)
        actor (op/build db)
        log   (atom [])
        exec! (fn [label tid request]
                (let [r (g/run* actor {:request request :context operator}
                                {:thread-id tid})]
                  (swap! log conj (step-fact label request r))
                  r))
        approve! (fn [label tid request]
                   (let [r (g/run* actor {:approval {:status :approved
                                                     :by (:actor-id operator)}}
                                   {:thread-id tid :resume? true})]
                     (swap! log conj (assoc (step-fact label request r)
                                            :human-approved? true))
                     r))]
    ;; --- bd-1: the full clean lifecycle -------------------------------
    (exec! "intake" "t1" {:op :mandate/intake :subject "bd-1"
                          :patch {:id "bd-1"
                                  :principal-seller "Sendai Rolling Mills Co"}})

    (exec!    "verify" "t2" {:op :mandate/verify :subject "bd-1"})
    (approve! "verify (approved)" "t2" {:op :mandate/verify :subject "bd-1"})

    (exec!    "confirm" "t3" {:op :deal/confirm :subject "bd-1"})
    (approve! "confirm (approved)" "t3" {:op :deal/confirm :subject "bd-1"})

    (exec!    "invoice" "t4" {:op :commission/invoice :subject "bd-1"})
    (approve! "invoice (approved)" "t4" {:op :commission/invoice :subject "bd-1"})

    ;; --- HARD holds ---------------------------------------------------
    (exec! "verify (unregistered jurisdiction)" "t5"
           {:op :mandate/verify :subject "bd-2"})
    (exec! "confirm (never verified)" "t6"
           {:op :deal/confirm :subject "bd-2"})

    (exec!    "verify" "t7" {:op :mandate/verify :subject "bd-3"})
    (approve! "verify (approved)" "t7" {:op :mandate/verify :subject "bd-3"})
    (exec!    "confirm (no mandate on file)" "t8"
              {:op :deal/confirm :subject "bd-3"})

    (exec!    "verify" "t9" {:op :mandate/verify :subject "bd-4"})
    (approve! "verify (approved)" "t9" {:op :mandate/verify :subject "bd-4"})
    (exec!    "confirm (buyer KYC not cleared)" "t10"
              {:op :deal/confirm :subject "bd-4"})

    (exec!    "verify" "t11" {:op :mandate/verify :subject "bd-5"})
    (approve! "verify (approved)" "t11" {:op :mandate/verify :subject "bd-5"})
    (exec!    "confirm (seller not sanctions-screened)" "t12"
              {:op :deal/confirm :subject "bd-5"})

    (exec!    "verify" "t13" {:op :mandate/verify :subject "bd-6"})
    (approve! "verify (approved)" "t13" {:op :mandate/verify :subject "bd-6"})
    (exec!    "confirm (undisclosed dual agency)" "t14"
              {:op :deal/confirm :subject "bd-6"})

    (exec! "confirm again" "t15" {:op :deal/confirm :subject "bd-1"})
    (exec! "invoice again" "t16" {:op :commission/invoice :subject "bd-1"})

    {:store db :runs @log}))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- kw-str [v]
  (if (keyword? v) (subs (str v) 1) (str v)))

(defn- td [& cells] (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- yes-no [b yes-class yes-text no-text]
  (if b
    (str "<span class=\"" yes-class "\">" yes-text "</span>")
    (str "<span class=\"muted\">" no-text "</span>")))

;; --- brokerage-deal directory ---------------------------------------

(defn- lifecycle-cell [{:keys [confirmed? invoiced? confirmation-number invoice-number]}]
  (cond
    invoiced?  (str "<span class=\"ok\">confirmed &amp; commission invoiced</span>"
                    "<br><span class=\"num\">" (esc confirmation-number) " / "
                    (esc invoice-number) "</span>")
    confirmed? (str "<span class=\"warn\">confirmed, commission not yet invoiced</span>"
                    "<br><span class=\"num\">" (esc confirmation-number) "</span>")
    :else      "<span class=\"muted\">not confirmed</span>"))

(defn- last-fact-cell [ledger deal-id]
  (let [f (last (filter #(= (:subject %) deal-id) ledger))]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (str/join ", " (map kw-str (:basis f)))) "</span>")
      (= :committed (:t f))
      (str "<span class=\"ok\">committed &middot; " (esc (kw-str (:op f))) "</span>")
      :else (str "<span class=\"muted\">" (esc (kw-str (:t f))) "</span>"))))

(defn- deal-row [ledger {:keys [id deal-id subject-matter principal-buyer
                                principal-seller jurisdiction mandate-side]
                         :as bd}]
  (td (code id)
      (esc deal-id)
      (esc subject-matter)
      (str (esc principal-buyer) "<br><span class=\"muted\">&larr; buyer / seller &rarr;</span><br>"
           (esc principal-seller))
      (esc jurisdiction)
      (code (kw-str mandate-side))
      (lifecycle-cell bd)
      (last-fact-cell ledger id)))

;; --- action gate (derived from live phase/governor values + this run) --

(defn- observed-dispositions
  "The dispositions this op ACTUALLY reached in the run, in first-seen
  order -- read off the real `run*` results, never asserted."
  [runs op]
  (->> runs (filter #(= op (:op %))) (map :disposition) distinct (remove nil?)))

(defn- gate-row [runs op]
  (let [ph        (:phase operator)
        {:keys [writes auto]} (get phase/phases ph)
        stakes?   (contains? governor/high-stakes op)
        clean     (phase/gate ph {:op op} :commit)
        observed  (observed-dispositions runs op)]
    (td (code op)
        (yes-no stakes? "warn" "always escalates" "not high-stakes")
        (yes-no (contains? writes op) "ok" "yes" "no")
        (yes-no (contains? auto op) "ok" "yes" "no")
        (str "<span class=\"" (if (= :commit (:disposition clean)) "ok" "warn") "\">"
             (esc (kw-str (:disposition clean))) "</span>"
             (when-let [r (:reason clean)]
               (str " <span class=\"muted\">" (esc (kw-str r)) "</span>")))
        (if (seq observed)
          (str/join ", " (map #(str "<span class=\""
                                    (case % :commit "ok" :hold "critical" "warn")
                                    "\">" (esc (kw-str %)) "</span>")
                              observed))
          "<span class=\"muted\">not exercised in this run</span>"))))

(defn- gate-ops
  "Every op in `commtrade.phase/write-ops`, ordered by first appearance
  in the real run log so the table reads in lifecycle order; any
  write-op the scenario never exercised is appended (sorted) rather
  than silently dropped."
  [runs]
  (let [seen (distinct (keep :op runs))
        rest' (sort-by str (remove (set seen) phase/write-ops))]
    (concat (filter phase/write-ops seen) rest')))

;; --- HARD holds ------------------------------------------------------

(defn- hold-rows [ledger]
  (for [f (filter #(= :governor-hold (:t %)) ledger)
        v (:violations f)]
    (td (str "<span class=\"critical\">" (esc (kw-str (:rule v))) "</span>")
        (code (kw-str (:op f)))
        (code (:subject f))
        (str "<span class=\"num\">" (esc (:confidence f)) "</span>")
        (esc (:detail v)))))

;; --- ledger ----------------------------------------------------------

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (td (esc (kw-str t))
      (code (kw-str (or op :n-a)))
      (code subject)
      (str "<span class=\"" (if (= :commit disposition) "ok" "critical") "\">"
           (esc (kw-str disposition)) "</span>")
      (esc (str/join " / " (map kw-str basis)))))

;; --- registry drafts -------------------------------------------------

(defn- record-row [r]
  (td (str "<span class=\"num\">" (esc (get r "record_id")) "</span>")
      (code (get r "kind"))
      (code (get r "deal_id"))
      (esc (get r "jurisdiction"))
      (esc (get r "immutable"))))

;; --- jurisdiction coverage -------------------------------------------

(defn- coverage-row [iso3]
  (let [{:keys [owner-authority legal-basis provenance required-evidence]}
        (facts/spec-basis iso3)]
    (td (code iso3)
        (esc owner-authority)
        (esc legal-basis)
        (str "<a href=\"" (esc provenance) "\">" (esc provenance) "</a>")
        (str "<span class=\"num\">" (count required-evidence) "</span>"))))

;; --- document --------------------------------------------------------

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn render
  "Renders the operator-console document from the result of
  `run-demo!` ({:store db :runs [..]}). Every interpolated value is
  HTML-escaped."
  [{:keys [store runs]}]
  (let [ledger   (vec (store/ledger store))
        deals    (store/all-brokerage-deals store)
        ;; Ask `facts/coverage` about the jurisdictions this run actually
        ;; touched (in deal order) PLUS every catalog entry, so a
        ;; jurisdiction a seeded deal really uses but the catalog does
        ;; NOT cover is reported as missing instead of being invisible.
        cov      (facts/coverage
                  (distinct (concat (map :jurisdiction deals)
                                    (sort (keys facts/catalog)))))]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<meta name=\"color-scheme\" content=\"light\">"
     "<title>cloud-itonami-isic-4610 &middot; wholesale on a fee or contract basis</title>"
     "<style>" (jp-go-dds.skin/dds+skin) "</style></head><body>\n"

     "<header class=\"bar\">\n"
     "  <h1>Wholesale on a fee or contract basis (ISIC 4610) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · deal confirmation &amp; commission invoicing always human-approved</span>\n"
     "</header>\n"

     "<main>\n"

     "  <section class=\"banner\">\n"
     "    <p>Everything below was produced by <strong>actually running this repo's actor</strong> at build time —\n"
     "      <code>commtrade.operation</code> (langgraph StateGraph) → <code>commtrade.governor</code>\n"
     "      → <code>commtrade.phase</code> → <code>commtrade.store</code> — over the seeded brokerage-deals in\n"
     "      <code>commtrade.store/demo-data</code>. Regenerate with <code>clojure -M:dev:render-html</code>.\n"
     "      No value on this page is hand-written domain data, and the page is byte-identical across reruns.</p>\n"
     "    <p class=\"muted\">A commission broker never takes title to the goods: <code>:deal/confirm</code> is the\n"
     "      broker's own record that a matched deal has been arranged (the two principals settle the underlying\n"
     "      trade directly between themselves), and <code>:commission/invoice</code> invoices the broker's own fee.</p>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Brokerage deals</h2>\n"
     "    <p class=\"muted\">Store state after the scenario. Confirmation / commission numbers are the ones\n"
     "      <code>commtrade.registry</code> actually issued during this run.</p>\n"
     (table ["Id" "Deal" "Subject matter" "Principals" "Jur." "Mandate side"
             "Lifecycle" "Last ledger fact"]
            (map (partial deal-row ledger) deals))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate</h2>\n"
     "    <p class=\"muted\">Read from live values, not prose: the high-stakes column is membership in\n"
     "      <code>commtrade.governor/high-stakes</code>, the phase columns are\n"
     "      <code>commtrade.phase/phases</code> at phase " (esc (:phase operator)) " (<code>"
     (esc (:label (get phase/phases (:phase operator)))) "</code>), the fifth column is what\n"
     "      <code>commtrade.phase/gate</code> returns when it is handed a governor-CLEAN\n"
     "      <code>:commit</code>, and the last column is what each op ACTUALLY reached in this run.</p>\n"
     "    <p class=\"muted\"><code>high-stakes</code> is a set of proposal <code>:stake</code> values;\n"
     "      this actor's advisor sets <code>:stake</code> equal to the op for the two actuation ops, so\n"
     "      membership is shown per op. <code>:deal/confirm</code> and <code>:commission/invoice</code> are\n"
     "      absent from every phase's <code>:auto</code> set, phase 3 included — two independent layers agree\n"
     "      that actuation is always a human call.</p>\n"
     (table ["Op" "Governor high-stakes" "Phase-3 write?" "Phase-3 auto?"
             "Phase gate on a clean verdict" "Observed in this run"]
            (map (partial gate-row runs) (gate-ops runs)))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>HARD governor holds (this run)</h2>\n"
     "    <p class=\"muted\">A HARD violation is un-overridable: <code>commtrade.phase/gate</code> keeps a\n"
     "      governor hold a hold at every phase, and the graph routes it straight to <code>:hold</code> —\n"
     "      it never reaches the <code>interrupt-before #{:request-approval}</code> pause, so no human is\n"
     "      ever asked to approve one. Rule names and detail text are read verbatim off the ledger.</p>\n"
     (table ["Rule" "Op" "Deal" "Advisor confidence" "Detail (as recorded)"]
            (hold-rows ledger))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every commit and every hold this scenario\n"
     "      produced, in order. <code>Basis</code> is the proposal's own citations for a commit, and the\n"
     "      violated rule names for a hold.</p>\n"
     (table ["Fact" "Op" "Deal" "Disposition" "Basis"]
            (map ledger-row ledger))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Registry drafts issued</h2>\n"
     "    <p class=\"muted\">Deal-confirmation and commission-invoice records built by\n"
     "      <code>commtrade.registry</code>. Every certificate this actor produces is UNSIGNED —\n"
     "      signature is the operator's act, not the actor's.</p>\n"
     (table ["Record id" "Kind" "Deal" "Jurisdiction" "Immutable"]
            (concat (map record-row (store/confirmation-history store))
                    (map record-row (store/invoice-history store))))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Jurisdiction spec-basis coverage</h2>\n"
     "    <p class=\"muted\"><code>commtrade.facts/coverage</code>, asked about every jurisdiction the\n"
     "      seeded deals use plus every catalog entry: <span class=\"num\">" (esc (:covered cov))
     "</span> of <span class=\"num\">" (esc (:requested cov)) "</span> have an official spec-basis; missing: "
     (if (seq (:missing-jurisdictions cov))
       (str "<span class=\"critical\">" (esc (str/join ", " (:missing-jurisdictions cov))) "</span>")
       "<span class=\"muted\">none</span>")
     ".\n"
     "      A jurisdiction absent from this table has NO spec-basis, and the governor HARD-holds any\n"
     "      proposal that tries to invent one (see <code>bd-2</code> above).</p>\n"
     (table ["ISO3" "Owner authority" "Legal basis" "Provenance" "Required evidence"]
            (map coverage-row (:covered-jurisdictions cov)))
     "    <p class=\"muted\">" (esc (:note cov)) "</p>\n"
     "  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>Generated by <code>commtrade.render-html</code> from a real actor run —\n"
     "     " (esc (count ledger)) " ledger facts, " (esc (count runs)) " graph runs, "
     (esc (count (filter #(= :governor-hold (:t %)) ledger))) " HARD holds.\n"
     "     Styled with <a href=\"https://github.com/kotoba-lang/jp-go-digital-design-system\">jp-go-dds</a>\n"
     "     (デジタル庁デザインシステム).</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out  (or (first args) "docs/samples/operator-console.html")
        demo (run-demo!)
        html (render demo)]
    (spit out html :encoding "UTF-8")
    (println "wrote" out
             "(" (count (store/ledger (:store demo))) "ledger facts,"
             (count (:runs demo)) "graph runs,"
             (count (store/confirmation-history (:store demo))) "deal confirmations,"
             (count (store/invoice-history (:store demo))) "commission invoices )")))
