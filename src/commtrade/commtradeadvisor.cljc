(ns commtrade.commtradeadvisor
  "CommTradeAdvisor client -- the *contained intelligence node* for the
  commission-brokerage (ISIC 4610, wholesale on a fee or contract
  basis) actor.

  It normalizes mandate intake, drafts a per-jurisdiction counterparty-
  diligence / dual-agency evidence checklist, drafts the deal-
  confirmation action, and drafts the commission-invoice action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real confirmation/settlement. Every output is
  censored downstream by `commtrade.governor` before anything touches
  the SSoT, and `:deal/confirm`/`:commission/invoice` proposals NEVER
  auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :deal/confirm | :commission/invoice | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [commtrade.facts :as facts]
            [commtrade.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the deal-id, either principal, the jurisdiction, the
  mandate side or any commercial value. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "仲立/取次案件記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :mandate/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-mandate
  "Per-jurisdiction counterparty-diligence / dual-agency evidence
  checklist draft. `:no-spec?` injects the failure mode we must defend
  against: proposing a checklist for a jurisdiction with NO official
  spec-basis in `commtrade.facts` -- the Commission Broker Governor
  must reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [bd (store/brokerage-deal db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction bd))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "commtrade.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :mandate-assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :mandate-assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-confirm
  "Draft the actual DEAL-CONFIRMATION action -- recording that a
  matched deal between two principals has been arranged and confirmed.
  This does NOT move goods or money between the principals (they settle
  the underlying trade directly between themselves); it is the
  broker's own record. ALWAYS `:stake :deal/confirm` -- this is a
  REAL-WORLD fiduciary act, never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`commtrade.phase`); the governor also always escalates on
  `:deal/confirm`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [bd (store/brokerage-deal db subject)
        mandate-ok? (and bd (some? (:mandate-terms bd)) (not= "" (:mandate-terms bd)))
        buyer-kyc-ok? (and bd (true? (:buyer-kyc-cleared? bd)))
        seller-kyc-ok? (and bd (true? (:seller-kyc-cleared? bd)))
        dual-agency-ok? (and bd (or (not= :dual (:mandate-side bd))
                                    (true? (:dual-agency-disclosed? bd))))
        sanctions-ok? (and bd (true? (:buyer-sanctions-screened? bd)) (true? (:seller-sanctions-screened? bd)))]
    {:summary    (str subject " 向け成約確認提案"
                      (when bd (str " (buyer=" (:principal-buyer bd)
                                    ", seller=" (:principal-seller bd) ")")))
     :rationale  (if bd
                   (str "mandate-on-file?=" mandate-ok?
                        " buyer-kyc-cleared?=" buyer-kyc-ok?
                        " seller-kyc-cleared?=" seller-kyc-ok?
                        " dual-agency-disclosed-if-applicable?=" dual-agency-ok?
                        " sanctions-screened-both?=" sanctions-ok?)
                   "brokerage-dealが見つかりません")
     :cites      (if bd [subject] [])
     :effect     :deal/mark-confirmed
     :value      {:deal-id subject}
     :stake      :deal/confirm
     :confidence (if (and mandate-ok? buyer-kyc-ok? seller-kyc-ok? dual-agency-ok? sanctions-ok?) 0.9 0.3)}))

(defn- propose-invoice
  "Draft the actual COMMISSION-INVOICE action -- invoicing the
  broker's OWN fee (never the underlying trade's settlement, which
  happens directly between the two principals). ALWAYS `:stake
  :commission/invoice` -- this is a REAL-WORLD act (real money moves
  from the mandating principal(s) to the broker), never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this
  op to a phase's `:auto` set (`commtrade.phase`); the governor also
  always escalates on `:commission/invoice`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [bd (store/brokerage-deal db subject)
        confirmed? (and bd (:confirmed? bd))
        sanctions-ok? (and bd (true? (:buyer-sanctions-screened? bd)) (true? (:seller-sanctions-screened? bd)))]
    {:summary    (str subject " 向け仲介手数料請求提案"
                      (when bd (str " (mandate-side=" (:mandate-side bd) ")")))
     :rationale  (if bd
                   (str "confirmed?=" confirmed?
                        " sanctions-screened-both?=" sanctions-ok?)
                   "brokerage-dealが見つかりません")
     :cites      (if bd [subject] [])
     :effect     :deal/mark-invoiced
     :value      {:deal-id subject}
     :stake      :commission/invoice
     :confidence (if (and confirmed? sanctions-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :mandate/intake     (normalize-intake db request)
    :mandate/verify     (verify-mandate db request)
    :deal/confirm       (propose-confirm db request)
    :commission/invoice (propose-invoice db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは仲立業(commission broker/仲立人)の成約確認・手数料請求"
       "エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:mandate/upsert|:mandate-assessment/set|:deal/mark-confirmed|"
       ":deal/mark-invoiced) "
       ":stake(:deal/confirm か :commission/invoice か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の仲立業・制裁要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"
       "仲立人はいかなる取引の目的物についても所有権を取得しない -- 成約確認は"
       "両当事者間の物・代金の移動そのものではなく、案件成立の記録に過ぎない。"
       "買主・売主どちらか一方の本人確認・制裁スクリーニングの状態を偽って報告"
       "してはいけない。双方代理(mandate-side :dual)の場合、開示・同意の記録が"
       "無い状態を『開示済み』と偽って報告してはいけない。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :mandate/verify     {:brokerage-deal (store/brokerage-deal st subject)}
    :deal/confirm       {:brokerage-deal (store/brokerage-deal st subject)}
    :commission/invoice {:brokerage-deal (store/brokerage-deal st subject)}
    {:brokerage-deal (store/brokerage-deal st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Commission Broker Governor
  escalates/holds -- an LLM hiccup can never auto-confirm a deal or
  auto-settle a commission invoice."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :commtradeadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
