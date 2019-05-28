(ns  aerial.saite.core
  (:require
   [cljs.core.async
    :as async
    :refer (<! >! put! chan)
    :refer-macros [go go-loop]]
   [clojure.string :as cljstr]

   [aerial.hanami.core
    :as hmi
    :refer [printchan user-msg
            re-com-xref xform-recom
            default-header-fn start
            update-adb get-adb
            get-vspec update-vspecs
            get-tab-field add-tab update-tab-field active-tabs
            md vgl app-stop]]
   [aerial.hanami.common
    :as hc
    :refer [RMV]]

   [aerial.saite.codemirror
    :as cm
    :refer [code-mirror get-cm-sexpr dbg-cm]]
   [aerial.saite.compiler
    :as comp
    :refer [evaluate expr*!]]

   [cljsjs.mathjax]

   [com.rpl.specter :as sp]

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




(defn react-hack
  "Monkey patching ... sigh ... This changes the (incorrect) behavior of
  React's removeChild and insertBefore functions which do not properly
  track changes in the DOM as effected by 3rd party libs. MathJax,
  Google Translate, etc. For details see
  `https://github.com/facebook/react/issues/11538`"
  []
  (when (and (fn? js/Node) js/Node.prototype)
    (let [orig-rm-child Node.prototype.removeChild
          new-rm-child
          (fn [child]
            (this-as this
              (if (not= child.parentNode this)
                (do (when js/console
                      (js/console.error
                       "Cannot remove a child from a different parent"
                       child this))
                    child)
                (do #_(printchan this (js-arguments))
                    (.apply orig-rm-child this (js-arguments))))))

          orig-ins-b4 Node.prototype.insertBefore
          new-ins-b4 (fn [newNode referenceNode]
                       (this-as this
                         (if (and referenceNode
                                  (not= referenceNode.parentNode this))
                           (do (when js/console
                                 (js/console.error
                                  "Cannot insert before a reference node "
                                  "from a different parent" referenceNode this))
                               newNode)
                           (do #_(printchan this (js-arguments))
                               (.apply orig-ins-b4 this (js-arguments))))))]
      (set! Node.prototype.removeChild new-rm-child)
      (set! Node.prototype.insertBefore new-ins-b4))))

(react-hack)




;;; Components ============================================================ ;;;




(defn cm-hiccup [opts input output]
  (let [id (opts :id)
        kwid (-> id name keyword)
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
      [v-box :gap "5px"
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
           :height (opts :out-height "100px")
           :justify (opts :justify :start)
           :align (opts :justify :stretch)
           :child [code-mirror output "clojure"
                   :js-cm-opts {:lineNumbers false,
                                :lineWrapping true}]]]]]]
      [gap :size "10px"]]]))


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
  )


(defn cm []
  (let [input (rgt/atom "")
        output (rgt/atom "")]
    (fn [& opts]
      (let [opts (->> opts (partition-all 2) (mapv vec)
                      (into {:id (gensym "cm-"), :size "auto"
                             :height "300px", :out-height "100px"}))
            kwid (name (opts :id))]
        (printchan :CM kwid :called :OPTS opts)
        (when-let [init (opts :src)] (reset! input init))
        (update-adb [:editors kwid] {:in input, :ot output, :opts opts})
        (cm-hiccup opts input output)))))




(defn saite-header []
  [h-box :gap "10px" :max-height "30px"
   :children [[gap :size "5px"]
              [:img {:src (get-adb [:main :logo])}]
              #_[title
               :level :level3
               :label [:span.bold (get-adb [:main :title])]]
              #_[gap :size "5px"]
              [hmi/session-input]
              [gap :size "5px"]
              [title
               :level :level3
               :label [:span.bold (get-adb [:main :uid :name])]]
              [gap :size "30px"]
              [h-box :gap "10px"
               :children [[md-circle-icon-button
                           :md-icon-name "zmdi-upload"
                           :tooltip "Upload Document"
                           :on-click
                           #(do (js/console.log "upload clicked"))]
                          [md-circle-icon-button
                           :md-icon-name "zmdi-download"
                           :tooltip "Save Document"
                           :on-click
                           #(do (js/console.log "download clicked"))]]]]])


(defn bar-slider-fn [tid val]
  (let [tabval (get-tab-field tid)
        spec-frame-pairs (tabval :spec-frame-pairs)]
    (printchan "Slider update " val)
    (update-tab-field tid :compvis nil)
    (update-tab-field
     tid :spec-frame-pairs
     (mapv (fn[[spec frame]]
             (let [cljspec spec
                   data (mapv (fn[m] (assoc m :b (+ (m :b) val)))
                              (get-in cljspec [:data :values]))
                   newspec (assoc-in cljspec [:data :values] data)]
               [newspec frame]))
           spec-frame-pairs))))

(defn instrumentor [{:keys [tabid spec]}]
  (printchan "Test Instrumentor called" :TID tabid #_:SPEC #_spec)
  (let [cljspec spec
        udata (cljspec :usermeta)]
    (update-adb [:udata] udata)

    (cond
      (not (map? udata)) []

      (udata :slider)
      (let [sval (rgt/atom "0.0")]
        (printchan :SLIDER-INSTRUMENTOR)
        {:top (xform-recom
               (udata :slider)
               :m1 sval
               :oc1 #(do (bar-slider-fn tabid %)
                         (reset! sval (str %)))
               :oc2 #(do (bar-slider-fn tabid (js/parseFloat %))
                         (reset! sval %)))})

      :else {}
      )))


(defn alert-panel [closefn]
  (printchan :alert-panel)
  [modal-panel
   :child [re-com.core/alert-box
           :id 1 :alert-type :warning
           :heading "Empty specification can't be rendered"
           :closeable? true
           :on-close closefn]
   :backdrop-color "grey" :backdrop-opacity 0.0])

(defn vis-panel [inspec donefn] (printchan :vis-panel)
  (go
    (if-let [otchart (get-adb [:main :otchart])]
      otchart
      (let [nm (get-adb [:main :uid :name])
            msg {:op :read-clj
                 :data {:session-name nm
                        :render? true
                        :cljstg inspec}}
            _ (hmi/send-msg msg)
            otspec (async/<! (get-adb [:main :convert-chan]))
            otchart (modal-panel
                     :backdrop-color   "grey"
                     :backdrop-opacity 0.4
                     :child [scroller
                             :max-height "700px"
                             :max-width "1000px"
                             :child [v-box
                                     :gap "10px"
                                     :children [[vgl otspec]
                                                [h-box :gap "5px" :justify :end
                                                 :children
                                                 [[md-circle-icon-button
                                                   :md-icon-name "zmdi-close"
                                                   :tooltip "Close"
                                                   :on-click donefn]]]]]])]
        (update-adb [:main :otchart] otchart)
        otchart))))

(defn tab<-> [tabval] (printchan "Make TAB<-> called ")
  (let [input (rgt/atom "")
        output (rgt/atom "")
        show? (rgt/atom false)
        alert? (rgt/atom false)
        process-done (fn[event]
                       (reset! show? false)
                       (update-adb [:main :otspec] :rm
                                   [:main :otchart] :rm))
        process-close (fn[event] (reset! alert? false))]
    (fn [tabval] (printchan "TAB<-> called ")
      [v-box :gap "5px"
       :children
       [[h-box :gap "10px" :justify :between
         :children
         [[h-box :gap "10px"
           :children
           [[gap :size "10px"]
            [md-circle-icon-button
             :md-icon-name "zmdi-circle-o"
             :tooltip "Clear"
             :on-click
             #(do (reset! input ""))]
            [md-circle-icon-button
             :md-icon-name "zmdi-fast-forward"
             :tooltip "Translate VGL -> VG -> Clj"
             :on-click
             #(reset! output
                      (if (= @input "")
                        ""
                        (try
                          (with-out-str
                            (-> (js/JSON.parse @input)
                                js/vl.compile .-spec
                                (js->clj :keywordize-keys true)
                                cljs.pprint/pprint))
                          (catch js/Error e (str e)))))]
            [md-circle-icon-button
             :md-icon-name "zmdi-caret-right-circle"
             :tooltip "Translate JSON to Clj"
             :on-click
             #(reset! output
                      (if (= @input "")
                        ""
                        (try
                          (with-out-str
                            (cljs.pprint/pprint
                             (js->clj (js/JSON.parse @input)
                                      :keywordize-keys true)))
                          (catch js/Error e (str e)))))]]]
          [h-box :gap "10px" :justify :end
           :children
           [[box :child (cond @alert?
                              [alert-panel process-close]
                              @show?
                              (get-adb [:main :otchart])
                              :else [p])]
            [md-circle-icon-button
             :md-icon-name "zmdi-caret-left-circle"
             :tooltip "Translate Clj to JSON"
             :on-click
             #(go (reset! input
                          (if (= @output "")
                            ""
                            (let [nm (get-adb [:main :uid :name])
                                  msg {:op :read-clj
                                       :data {:session-name nm
                                              :render? false
                                              :cljstg @output}}]
                              (hmi/send-msg msg)
                              (async/<! (get-adb [:main :convert-chan]))))))]
            [md-circle-icon-button
             :md-icon-name "zmdi-caret-up-circle"
             :tooltip "Render in Popup"
             :on-click #(if (= @output "")
                          (reset! alert? true)
                          (let [ch (vis-panel @output process-done)]
                            (go (async/<! ch)
                                (reset! show? true))))]
            [md-circle-icon-button
             :md-icon-name "zmdi-circle-o"
             :tooltip "Clear"
             :on-click
             #(do (reset! output ""))]
            [gap :size "10px"]]]]]
        [line]
        [h-split
         :panel-1 [box :size "auto"
                   :child [code-mirror input {:name "javascript", :json true}]]
         :panel-2 [box :size "auto"
                   :child [code-mirror output "clojure"]]
         :size    "auto", :width "1050px", :height "600px"]]])))


;;; Messaging ============================================================ ;;;


(defmethod user-msg :data [msg]
  (printchan :DATA msg))


(defmethod user-msg :clj-read [msg]
  (let [data (msg :data)
        render? (data :render?)
        clj (dissoc data :render?)
        result (if render?
                 clj
                 (try (-> clj clj->js (js/JSON.stringify nil, 2))
                      (catch js/Error e (str e))))
        ch (get-adb [:main :convert-chan])]
    #_(printchan render? result)
    (go (async/>! ch result))))


(defmethod user-msg :app-init [msg]
  (update-adb [:main :convert-chan] (async/chan))
  (update-adb [:editors] {})
  (add-tab {:id :xvgl
            :label "<->"
            :opts {:extfn (tab<-> :NA)}}))


(defn update-data
  "Originally meant as general updater of vis plot/chart data
  values. But to _render_ these, requires knowledge of the application
  pages/structure. So, this is not currently used. If we can figure
  out Vega chageSets and how they update, we may be able to make this
  a general op in Hanami."
  [data-maps]
  (printchan :UPDATE-DATA data-maps)
  #_(mapv (fn [{:keys [usermeta data]}]
          (let [vid (usermeta :vid)
                spec (dissoc (get-vspec vid) :data)]
            (assoc-in spec [:data :values] data)))
        data-maps))

(defmethod user-msg :data [msg]
  (update-data (msg :data)))




;;; Call Backs ============================================================ ;;;

#_(js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub])
#_(js/MathJax.Hub.Queue
   #js ["Typeset" js/MathJax.Hub
        (js/document.getElementById (get-adb [:mathjax]))])

(def mathjax-chan (async/chan 10))

(go-loop [id (async/<! mathjax-chan)]
  (let [elt (js/document.getElementById id)
        _ (printchan :ID id, :ELT elt)
        jsvec (when elt (clj->js ["Typeset" js/MathJax.Hub elt]))]
    (async/<! (async/timeout 50))
    (cond
      ::global (js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub])
      jsvec    (js/MathJax.Hub.Queue jsvec)
      :else    (async/>! mathjax-chan id)))
  (recur (async/<! mathjax-chan)))


(defn frame-callback
  ([]
   (go (async/>! mathjax-chan ::global)))
  ([spec frame] (printchan :FRAME-CALLBACK :spec spec)
   (let [id (frame :frameid)]
     (go (async/>! mathjax-chan id))
     [spec frame])))


(defn symxlate-callback [sym]
  (let [snm (name sym)]
    (cond ;; (= snm "md") md
          (= snm "cm") (cm)
          :else sym)))


;;; Startup ============================================================== ;;;


#_(when-let [elem (js/document.querySelector "#app")]
  (hc/add-defaults
   :HEIGHT 400 :WIDTH 450
   :USERDATA {:tab {:id :TID, :label :TLBL, :opts :TOPTS}
              :frame {:top :TOP, :bottom :BOTTOM, :left :LEFT, :right :RIGHT}
              :opts :OPTS
              :vid :VID,
              :msgop :MSGOP,
              :session-name :SESSION-NAME}
   :VID RMV, :MSGOP :tabs, :SESSION-NAME "Exploring"
   :TID :expl1, :TLBL #(-> :TID % name cljstr/capitalize)
   :OPTS (hc/default-opts :vgl)
   :TOPTS {:order :row, :eltsper 2 :size "auto"})
  (start :elem elem
         :port js/location.port
         :symxlate-cb symxlate-callback
         :frame-cb frame-callback
         :instrumentor-fn instrumentor))



(comment

  (when-let [elem (js/document.querySelector "#app")]
    (hc/add-defaults
     :HEIGHT 400 :WIDTH 450
     :USERDATA {:tab {:id :TID, :label :TLBL, :opts :TOPTS}
                :frame {:top :TOP, :bottom :BOTTOM, :left :LEFT, :right :RIGHT}
                :opts :OPTS
                :vid :VID,
                :msgop :MSGOP,
                :session-name :SESSION-NAME}
     :VID RMV, :MSGOP :tabs, :SESSION-NAME "Exploring"
     :TID :expl1, :TLBL #(-> :TID % name cljstr/capitalize)
     :OPTS (hc/default-opts :vgl)
     :TOPTS {:order :row, :eltsper 2 :size "auto"})
    (start :elem elem
           :port 3000
           :symxlate-cb symxlate-callback
           :frame-cb frame-callback
           :header-fn saite-header
           :instrumentor-fn instrumentor))


  (let[frame (js/document.getElementById "frame-64")]
    (js/console.log (aget frame.childNodes 2)))

  (let[frame (js/document.getElementById "frame-64")
       bottom (aget frame.childNodes 4)]
    (js/console.log bottom.childNodes))



  (defn foo []
    (sp/select [sp/ATOM :tabs :active sp/ATOM sp/ALL]
               hmi/app-db))
  (->> (foo)
       (mapv (fn[tab] (mapv #(tab %) [:id :label :opts :specs]))))

  (->> (foo)
       ((fn [tabs]
          (mapv (fn[tab]
                  (let [[id label opts specs] (mapv #(tab %)
                                                    [:id :label :opts :specs])
                        specs (mapv
                               (fn [spec]
                                 (let [frame (or (-> spec :usermeta :frame) {})
                                       [top bottom left right]
                                       (mapv frame [:top :bottom :left :right])]
                                   frame))
                               specs)]
                    [id label opts specs]))
                tabs))))

  (def CM-VALS
    (sp/recursive-path
     [] p
     (sp/cond-path #(and (vector? %)
                         (and (-> % first symbol?)
                              (= (-> % first name) "cm")))
                   sp/STAY
                   vector? [sp/ALL p])))

  (let [hiccup (sp/transform
                [sp/ALL CM-VALS]
                (fn[cm]
                  (let [m (->> cm rest (partition-all 2) (mapv vec) (into {}))
                        id (m :id)
                        ed (get-adb [:editors id])
                        instg (deref (ed :in))
                        otstg (deref (ed :ot))
                        opts (merge (ed :opts) {:instg instg :otstg otstg} m)]
                    [(-> cm first name symbol) ::opts opts]))
                '[[aerial.saite.examples/gap :size "10px"]
                  [aerial.saite.examples/cm :id "cm-scatter-1"]])]
    [(->> hiccup second first (= 'cm))
     (->> hiccup second second (= ::opts))])

  (sp/select [sp/ALL CM-VALS] '[[aerial.saite.examples/gap :size "10px"]
                                [aerial.saite.examples/cm :id "cm-scatter-1"]])

  cljs.reader/read-string

  (def paratxt2 (js/document.createTextNode "\\(f(x) = \\sqrt x \\)"))
  (def para (js/document.createElement "P"))
  (.appendChild para paratxt2)
  (do
    (.replaceChild divelt para (aget divelt.childNodes 0))
    (js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub para]))

  (js/document.createElement "a")

  )
