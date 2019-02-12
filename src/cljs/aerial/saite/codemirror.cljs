(ns  aerial.saite.codemirror
  (:require
   [cljs.core.async
    :as async
    :refer (<! >! put! chan)
    :refer-macros [go go-loop]]
   [clojure.string :as cljstr]

   [aerial.hanami.core :as hmi :refer [printchan]]
   [aerial.hanami.common :as hc :refer [RMV]]

   [com.rpl.specter :as sp]

   [reagent.core :as rgt]

   [cljsjs.codemirror]
   [cljsjs.codemirror.mode.clojure]
   [cljsjs.codemirror.mode.javascript]
   [cljsjs.codemirror.addon.hint.show-hint]

   [cljsjs.codemirror.addon.comment.comment]
   [cljsjs.codemirror.addon.dialog.dialog]
   [cljsjs.codemirror.addon.display.panel]

   [cljsjs.codemirror.addon.search.search]
   [cljsjs.codemirror.addon.search.searchcursor]
   [cljsjs.codemirror.addon.search.jump-to-line]

   [cljsjs.codemirror.addon.edit.closebrackets]
   [cljsjs.codemirror.addon.edit.matchbrackets]

   [cljsjs.codemirror.keymap.emacs]
   [cljsjs.codemirror.keymap.sublime]
   [cljsjs.codemirror.keymap.vim]

   [cljsjs.highlight]
   [cljsjs.highlight.langs.clojure]
   [cljsjs.highlight.langs.javascript]

   [re-com.core
    :as rcm
    :refer [h-box v-box box gap line h-split v-split
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



(defn code-mirror
  "Create a code-mirror editor. The parameters:
  value-atom (reagent atom)
    when this changes, the editor will update to reflect it.
  options
  :js-cm-opts
    options passed into the CodeMirror constructor"
  [input mode & {:keys [js-cm-opts]}]
  (printchan "CODE-MIRROR called")
  (let [cm (atom nil)]
    (rgt/create-class
     {:display-name "CMirror"

      :component-did-mount
      (fn [comp]
        (printchan "CM did-mount called")
        #_(js/console.log comp, (rgt/dom-node comp))
        (let [opts (clj->js (merge
                             {:lineNumbers true
                              :lineWrapping false,
                              :viewportMargin js/Infinity
                              :autofocus true
                              :keyMap "emacs"
                              :matchBrackets true
                              :autoCloseBrackets true
                              :value @input
                              :mode mode}
                             js-cm-opts))
              inst (.fromTextArea js/CodeMirror (rgt/dom-node comp) opts)]

          (.setValue inst @input)
          (reset! cm inst)
          (.on inst "change" #_#(reset! input (.getValue %))
               (fn []
                 (let [value (.getValue inst)]
                   (when true #_(not= value @input)
                     (reset! input value) #_(on-change value)))))
          ))

      :component-did-update
      (fn [comp old-argv]
        (printchan "CM did-update called")
        (when (not= @input (.getValue @cm))
          (.setValue @cm @input)
          ;; reset the cursor to the end of the text, if the text was
          ;; changed externally
          (let [last-line (.lastLine @cm)
                last-ch (count (.getLine @cm last-line))]
            (.setCursor @cm last-line last-ch))))

      :reagent-render
      (fn [_ _ _]
        @input
        [:textarea {:rows "20",
                    #_:style #_"flex: 0 0 auto; padding-right: 12px;"}])})))
