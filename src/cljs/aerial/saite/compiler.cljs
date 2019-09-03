(ns aerial.saite.compiler
  (:require-macros
   [aerial.saite.analyzer :refer [analyzer-state]])

  (:require
   [clojure.string :as cljstr]

   [goog.string :as gstring]
   [goog.string.format]

   [cljs.tools.reader :refer [read-string]]
   [cljs.env :as env]
   [cljs.js :refer [empty-state eval js-eval eval-str require]]

   [aerial.hanami.core
    :as hmi
    :refer [printchan user-msg
            re-com-xref xform-recom
            default-header-fn start
            get-vspec update-vspecs
            get-tab-field add-tab update-tab-field
            add-to-tab-body remove-from-tab-body
            active-tabs
            md vgl app-stop]]
   [aerial.hanami.common
    :as hc
    :refer [RMV]]
   [aerial.hanami.templates :as ht]

   [reagent.core :as rgt]

   [re-com.core
    :as rcm
    :refer [h-box v-box box gap line h-split v-split scroller
            button row-button md-icon-button md-circle-icon-button info-button
            input-text input-password input-textarea
            label title p
            single-dropdown
            checkbox radio-button slider progress-bar throbber
            horizontal-bar-tabs vertical-bar-tabs
            modal-panel popover-content-wrapper popover-anchor-wrapper]
    :refer-macros [handler-fn]]
   [re-com.box
    :refer [flex-child-style]]
   [re-com.dropdown
    :refer [filter-choices-by-keyword single-dropdown-args-desc]]
   ))




(defn format [s & params]
  (apply gstring/format s params))

(def state (cljs.js/empty-state))

(defn loader-fn [info-map cb]
  (printchan info-map)
  (cb  {:lang :js :source ""}))

(defn evaluate
  ([nssym source cb]
   (if (string? source)
     (let [source (cljstr/replace source #"hmi/" "aerial.hanami.core/")
           source (cljstr/replace source #"hcm/" "aerial.hanami.common/")
           source (cljstr/replace source #"htm/" "aerial.hanami.templates/")]
       (cljs.js/eval-str state source nil
                         {:eval cljs.js/js-eval
                          :ns nssym
                          :load loader-fn
                          :context :expr}
                         cb))
     (try
       (cljs.js/eval state source
                     {:eval cljs.js/js-eval
                      :ns nssym
                      :context :expr}
                     cb)
       (catch :default cause
         (cb {:error (prn-str cause)})))))
  ([source cb]
   (evaluate 'aerial.saite.compiler source cb)))


(def base-requires
  "[clojure.string :as str]
         [aerial.hanami.core :as hmi :refer [md]]
         [aerial.hanami.common :as hc :refer [RMV]]
         [aerial.hanami.templates :as ht]
         [aerial.saite.core :as sc :refer [read-data]]
         [com.rpl.specter :as sp]")

(defn add-requires [base requires]
  (reduce (fn[R r]
            (format (str R "\n         %s") r))
          base-requires requires))

(defn set-namespace
  [nssym & {:keys [base requires]
            :or {base base-requires}}]
  (evaluate
   (format
    "(ns %s
       (:require
         %s))"
    (name nssym)
    (add-requires base requires))
   println))


#_(ambient.main.core/analyzer-state 'aerial.hanami.core)
(defn load-analysis-cache! []
  (cljs.js/load-analysis-cache!
   state 'aerial.saite.compiler
   (analyzer-state 'aerial.saite.compiler))
  (cljs.js/load-analysis-cache!
   state 'aerial.saite.core
   (analyzer-state 'aerial.saite.core))
  (cljs.js/load-analysis-cache!
   state 'aerial.hanami.core
   (analyzer-state 'aerial.hanami.core))
  (cljs.js/load-analysis-cache!
   state 'aerial.hanami.templates
   (analyzer-state 'aerial.hanami.templates))
  (cljs.js/load-analysis-cache!
   state 'aerial.hanami.common
   (analyzer-state 'aerial.hanami.common))
  :done)


(def expr* (atom nil))
(defn expr*! [x] (reset! expr* x))

(load-analysis-cache!)
