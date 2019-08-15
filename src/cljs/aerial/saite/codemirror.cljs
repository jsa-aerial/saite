(ns  aerial.saite.codemirror
  (:require
   [cljs.core.async
    :as async
    :refer (<! >! put! chan)
    :refer-macros [go go-loop]]
   [clojure.string :as cljstr]

   [cljs.tools.reader :refer [read-string]]

   [aerial.hanami.core :as hmi
    :refer [printchan md update-adb get-adb]]
   [aerial.hanami.common :as hc
    :refer [RMV]]
   [aerial.hanami.templates :as ht]

   [aerial.saite.compiler
    :refer [format evaluate load-analysis-cache!]]
   [aerial.saite.tabops :as tops
    :refer [push-undo undo redo]]

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
    :refer [h-box v-box box border gap line h-split v-split scroller
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



(defn init []
  (load-analysis-cache!))



(defn get-cm-sexpr [cm]
  (let [end (.getCursor cm)
        start (do (((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-B") cm)
                  (.getCursor cm))
        stgval (.getValue cm)
        lines (subvec (clojure.string/split-lines stgval)
                      start.line (inc end.line))
        begstg (-> lines first (subs start.ch))
        endstg (-> lines last (subs 0 end.ch))]
    (.setCursor cm end)
    (if (= start.line end.line)
      (let [l (first lines)]
        (subs l start.ch end.ch))
      (let [midstg (clojure.string/join
                    "\n" (subvec lines 1 (dec (count lines))))]
        (clojure.string/join "\n" [begstg midstg endstg])))))

(defn find-outer-sexpr [cm]
  (let [f ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-U")]
    (loop [pos (do (f cm) (.getCursor cm))]
      (if (= 0 pos.ch)
        pos
        (recur (do (f cm) (.getCursor cm)))))))

(defn get-outer-sexpr-src [cm]
  (let [pos (.getCursor cm)
        _ (find-outer-sexpr cm)
        _ (((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-F") cm)
        s (get-cm-sexpr cm)]
    (.setCursor cm pos)
    s))


(def LIST-NODES
  (sp/recursive-path
   [] p
   (sp/if-path
    list? (sp/continue-then-stay sp/ALL p))))


#_(let [cm @dbg-cm] (read-string (str \( (.getValue cm) "\n" \))))
(defn get-all-cm-as-code [cm]
  (read-string (str \( (.getValue cm) "\n" \))))

(defn get-cm-frame-ids [cm]
  (let [l (get-all-cm-as-code cm)]
    (->> l (sp/select [LIST-NODES #(= (first %) 'hc/xform)])
         (mapv (fn[fm] (->> fm (drop-while #(not= % :FID)) second))))))

(defn get-tab-specs [tid]
  (sp/select-one [sp/ATOM :tabs :active sp/ATOM sp/ALL
                  #(= (% :id) tid) :specs]
                 hmi/app-db))


(defn get-tab-frame-ids [tid]
  (mapv #(-> % :usermeta :frame :fid) (get-tab-specs tid)))

(defn frame-exists? [fid]
  (let [tid (hmi/get-cur-tab :id)]
    (some #{fid} (get-tab-frame-ids tid))))

(defn get-frame-index [cm]
  (->> cm get-outer-sexpr-src read-string
       (sp/select-first [LIST-NODES #(= (first %) 'hc/xform)])
       (drop-while #(not= % :FID)) second))

(defn get-frame-index-positions [cm]
  (let [fid (get-frame-index cm)
        tab-fids (get-tab-frame-ids :chap1)
        cm-fids (get-cm-frame-ids cm)]
    {:fid fid
     :tab-pos (keep-indexed #(when (= fid %2) %1) tab-fids)
     :cm-pos  (keep-indexed #(when (= fid %2) %1) cm-fids)
     :tab-fids tab-fids
     :cm-fids cm-fids}))

(defn current-cm-frame-info [cm]
  (let [fidinfo (get-frame-index-positions cm)
        fid (fidinfo :fid)
        cm-fids (fidinfo :cm-fids)
        tab-fids (fidinfo :tab-fids)
        b4ids (->> cm-fids (take-while #(not= % fid)) (into #{}))
        tb4ids (keep #(b4ids %) tab-fids)
        locid (cond (not (seq b4ids)) :beg
                    (seq tb4ids) (last tb4ids)
                    :else :end)]
    {:fid fid :locid locid
     :tabfid (if (seq tab-fids) (frame-exists? fid) nil)
     :b4ids b4ids :tb4ids tb4ids}))



(defn get-cm-block [cm]
  (let [pos (.getCursor cm)
        line pos.line
        ch pos.ch
        stgval (.getValue cm)
        lines (cljstr/split-lines stgval)
        find-hd (fn [l dir]
                  (let [f (if (= dir :bkwd) dec inc)
                        stop (if (= dir :bkwd) 0 (-> lines count dec))]
                    (loop [l l]
                      (let [line (lines l)]
                        (if (or (= l stop) (cljstr/starts-with? line ";@@@"))
                          [l line]
                          (recur (f l)))))))
        [start l1] (find-hd line :bkwd)
        [end l2]   (find-hd line :fwd)
        block      (->> (subvec lines start end)
                        (filter #(not (cljstr/starts-with? % ";;"))))
        hd (-> block first (cljstr/replace #";@@@[ ]*" ":")
               (cljstr/split #" +"))
        blockstg (cljstr/join "\n" (rest block))]
    #_(println line ch start l1 end l2 hd)
    [hd blockstg]))

(defn get-chunk-code [lines]
  (let [v (atom [])]
    (doseq [l lines]
      (evaluate
       l #(swap! v (fn[v] (conj v (if (% :value) (% :value) %))))))
    @v))


(defmulti gen-block-code
  (fn[hd blines] (first hd)))

(defmethod gen-block-code :MD
  [hd blines]
  (let [id (second hd)
        margin? (cljstr/starts-with? (first blines) ":margin")
        margin (if margin?
                 (-> blines first (cljstr/split #" " ) second)
                 "20px")
        blines (if margin? (rest blines) blines)
        body (cljstr/join "\n" blines)]
    (format "(hmi/add-to-tab-body
              (hmi/get-cur-tab :id)
              (hc/xform
               ht/empty-chart :FID %s
               :TOP '[[gap :size %s]
                      [md %s]]))" id margin body)))

(defn get-body [hdr bodystg]
  (let [[kind id] hdr
        body-lines (cljstr/split-lines bodystg)]
    (gen-block-code hdr body-lines)))

(defn process-cm-block [cm]
  (let [[hd blockstg] (get-cm-block cm)
        hd-code (get-chunk-code hd)
        body (get-body hd-code blockstg)]
    body))




(def dbg-cm (atom nil))


(defn insert-frame [cm]
  (let [{:keys [fid locid tabfid]} (current-cm-frame-info @dbg-cm)
        src (get-outer-sexpr-src cm)
        pos (if (= locid :beg) :same :after)
        res (volatile! nil)
        _ (evaluate src (fn[v] (vswap! res #(do v))))
        picframe (or (@res :value)
                     (let [e (js->clj(@res :error))]
                       {:usermeta
                        {:frame {:fid fid
                                 :top "<p>" e.cause.message "</p>"}}}))]
    (when (and fid (not tabfid))
      (tops/add-frame picframe locid pos))))

(defn delete-frame [cm]
  (let [{:keys [fid locid tabfid]} (current-cm-frame-info @dbg-cm)]
    (when (and fid tabfid)
      (tops/remove-frame tabfid))))


;;#(reset! expr* %)
(defn evalxe [cm] (reset! dbg-cm cm)
  (let [cb cm.CB]
    (if-let [source (get-cm-sexpr cm)]
      (evaluate source cb)
      (cb {:value "not on sexpr"}))))

(defn evalcc [cm] (reset! dbg-cm cm)
  (let [cb cm.CB
        pos (.getCursor cm)]
    #_(js/console.log (find-outer-sexpr cm))
    (find-outer-sexpr cm)
    (((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-F") cm)
    (evaluate (get-cm-sexpr cm) cb)
    (.setCursor cm pos)))


(defn xtra-keys-emacs []
  (CodeMirror.normalizeKeyMap
   (js->clj {"Ctrl-F"    ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-F")
             "Ctrl-B"    ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-B")
             "Ctrl-Home" ((js->clj CodeMirror.keyMap.emacs) "Shift-Alt-,")
             "Ctrl-End"  ((js->clj CodeMirror.keyMap.emacs) "Shift-Alt-.")
             "Alt-W"     ((js->clj CodeMirror.keyMap.emacs) "Ctrl-W")
             "Alt-K"     ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-K")
             "Ctrl-X R"  ((js->clj CodeMirror.keyMap.emacs) "Shift-Alt-5")
             "Insert"    insert-frame
             "Delete"    delete-frame
             "Ctrl-X Ctrl-E" evalxe
             "Ctrl-X Ctrl-C" evalcc
             })))


(defn code-mirror
  "Create a code-mirror editor. The parameters:
  value-atom (reagent atom)
    when this changes, the editor will update to reflect it.
  options
  :js-cm-opts
    options passed into the CodeMirror constructor"
  [input mode & {:keys [js-cm-opts cb] :or {cb #(printchan %)}}]
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
                              :extraKeys (xtra-keys-emacs)
                              :matchBrackets true
                              :autoCloseBrackets true
                              :value @input
                              :mode mode}
                             js-cm-opts))
              inst (.fromTextArea js/CodeMirror (rgt/dom-node comp) opts)]

          (.setValue inst @input)
          (set! (.-CB inst) cb)
          (reset! cm inst)
          (reset! dbg-cm inst)
          (.on inst "change" #_#(reset! input (.getValue %))
               (fn []
                 (let [value (.getValue inst)]
                   #_(printchan :INPUTDM @input :CMDM value)
                   (when #_true (not= value @input)
                     (reset! input value) #_(on-change value)))))
          ))

      :component-did-update
      (fn [comp old-argv]
        (printchan "CM did-update called")
        (when (not= @input (.getValue @cm))
          #_(printchan :INPUT @input :CM (.getValue @cm))
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




(defn cm-hiccup [opts input output]
  (let [id (opts :id)
        kwid (-> id name keyword)
        layout (if (= (opts :layout) :up-down) v-box h-box)
        ch (opts :height "400px")
        oh (opts :out-height "100px")]
    [h-box :gap "5px" :attr {:id id}
     :children
     [[v-box :gap "5px"
       :children
       [[md-circle-icon-button
         :md-icon-name "zmdi-caret-right-circle"
         :tooltip "Eval Code"
         :on-click #(printchan :ID id :VID (opts :vid) :eval @input)]
        [md-circle-icon-button
         :md-icon-name "zmdi-circle-o"
         :tooltip "Clear"
         :on-click
         #(do (reset! output ""))]]]
      [layout :gap "5px"
       :width (opts :width "500px")
       :height (+ ch oh 50)
       :children
       [[v-box
         :children
         [[box
           :size (opts :size "auto")
           :width (opts :width "500px")
           :height (opts :height "300px")
           :justify (opts :justify :start)
           :align (opts :justify :stretch)
           :child [code-mirror input "clojure"
                   :cb (fn[m]
                         (let [ostg (with-out-str
                                      (cljs.pprint/pprint
                                       (or (m :value) (m :error))))]
                           (reset! output (str @output
                                               "=> " ostg))
                           (printchan :output @output)))]]]]
        [v-box
         :children
         [[box
           :size (opts :size "auto")
           :width (opts :width "500px")
           :height oh
           :justify (opts :justify :start)
           :align (opts :justify :stretch)
           :child [code-mirror output "clojure"
                   :js-cm-opts {:lineNumbers false,
                                :lineWrapping true}]]]]]]
      [gap :size "10px"]]]))


(defn cm []
  (let [input (rgt/atom "")
        output (rgt/atom "")]
    (fn [& opts]
      (let [opts (->> opts (partition-all 2) (mapv vec) (into {}))
            kwid (name (opts :id (gensym "cm-")))
            opts (or (get-adb [:editors kwid :opts])
                     (merge {:id kwid, :size "auto" :layout :up-down
                             :height "300px", :out-height "100px"}
                            opts))
            _ (if (and (opts :src) (= @input "")) (reset! input (opts :src)))
            input (or (get-adb [:editors kwid :in]) input)
            output (or (get-adb [:editors kwid :ot]) output)]
        (when-not (get-adb [:editors kwid])
          (update-adb [:editors kwid] {:in input, :ot output, :opts opts})
          (printchan :CM kwid :called :OPTS opts))
        [cm-hiccup opts input output]))))




(comment

  (defn vis! [vid picid]
    (hmi/visualize
     (get-vspec vid)
     (js/document.getElementById picid)))

  (vis! :bc1 "scatter-1")
  (vis! :sqrt "scatter-1")
  (vis! :scatter-1 "scatter-1")

  (when-let [source (get-cm-sexpr @dbg-cm)]
    (evaluate source #(printchan (expr*! %))))

  (js/console.log (.setCursor @dbg-cm 2 42))
  (js/console.log (.getCursor @dbg-cm))
  (js/console.log (.findMatchingBracket @dbg-cm (.getCursor @dbg-cm)))





  (let [cm @dbg-cm
        cb cm.CB]
    (evaluate (process-cm-block cm) cb))

  (let [src (prn-str (xform-tab-data (get-tab-data)))
        res (atom {})]
    (evaluate src (fn[x] (reset! res x)))
    (if-let [v (:value @res)]
      v
      @res))

  (let [src (prn-str (get-tab-data))
        res (atom {})]
    (evaluate src (fn[x] (reset! res  (x :value))))
    (get-in @res [0 :chap1 :opts :wrapfn :fn]))





  (if-let [bounds (.findMatchingBracket @dbg-cm (.getCursor @dbg-cm))]
    (.setCursor @dbg-cm bounds.to))

  (if-let [bounds (.findMatchingBracket @dbg-cm (.getCursor @dbg-cm))]
    (let [start bounds.to
          end bounds.from
          stgval (.getValue @dbg-cm)
          lines (subvec (clojure.string/split-lines stgval)
                        start.line (inc end.line))
          begstg (-> lines first (subs start.ch))
          endstg (-> lines last (subs 0 (inc end.ch)))]
      (if (= start.line end.line)
        (subs begstg 0 end.ch)
        (let [midstg (clojure.string/join
                      " " (subvec lines 1 (dec (count lines))))]
          (clojure.string/join " " [begstg midstg endstg]))))
    "cursor not at sexpr end")

  js/CodeMirror.keyNames
  js/CodeMirror.keyMap
  js/CodeMirror.keyMap.emacs

  )
