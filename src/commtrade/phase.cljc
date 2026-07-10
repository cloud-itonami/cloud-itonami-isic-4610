(ns commtrade.phase
  "Phase 0->3 staged rollout for the commission-brokerage actor.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- mandate intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds mandate-verification writes,
                                 still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:mandate/intake` (no fiduciary/
                                 sanctions risk yet) may auto-commit.
                                 `:deal/confirm`/`:commission/invoice`
                                 NEVER auto-commit, at any phase.

  `:deal/confirm`/`:commission/invoice` are deliberately ABSENT from
  every phase's `:auto` set, including phase 3 -- a permanent
  structural fact, not a rollout milestone still to come. Confirming a
  real matched deal between two principals (the broker's own record
  that a deal has been arranged -- not a transfer of the underlying
  goods or money, which settle directly between the two principals)
  and settling a real commission invoice (real money moving from the
  mandating principal(s) to the broker) are the two real-world
  fiduciary/commercial acts this actor performs; both are always a
  human trading supervisor's call. `commtrade.governor`'s `:deal/
  confirm`/`:commission/invoice` high-stakes gate enforces the same
  invariant independently -- two layers, not one, agree on this. Like
  every prior sibling's phase 3 `:auto` set, this domain has only ONE
  member (`:mandate/intake`) -- no separate no-risk lifecycle distinct
  from the brokerage-deal itself.")

(def read-ops  #{})
(def write-ops #{:mandate/intake :mandate/verify :deal/confirm :commission/invoice})

;; NOTE the invariant: `:deal/confirm`/`:commission/invoice` are members
;; of `write-ops` (governor-gated like any write) but are NEVER members
;; of any phase's `:auto` set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                :auto #{}}
   1 {:label "assisted-intake"  :writes #{:mandate/intake}                                  :auto #{}}
   2 {:label "assisted-verify"  :writes #{:mandate/intake :mandate/verify}                  :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:mandate/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:deal/confirm`/`:commission/invoice` are never auto-eligible at
    any phase, so they always escalate once the governor clears them
    (or hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Commission Broker Governor verdict to a base disposition
  before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
