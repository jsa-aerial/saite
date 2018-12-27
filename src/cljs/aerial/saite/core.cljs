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
            vgl app-stop]]
   [aerial.hanami.common
    :as hc
    :refer [RMV]]

   [aerial.saite.codemirror
    :as cm
    :refer [code-mirror]]

   [com.rpl.specter :as sp]

   [reagent.core :as rgt]

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


;;; Components ============================================================ ;;;

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

(defn instrumentor [{:keys [tabid spec opts]}]
  (printchan "Test Instrumentor called" :TID tabid #_:SPEC #_spec)
  (let [cljspec spec
        udata (cljspec :usermeta)
        default-frame {:top [], :bottom [],
                       :left [[box :size "0px" :child ""]],
                       :right [[box :size "0px" :child ""]]}]
    (update-adb [:udata] udata)
    (cond
      (not (map? udata)) []

      (udata :frame)
      (let [frame-sides (udata :frame)]
        (printchan :Frame-Instrumentor)
        (update-adb [:dbg :frame]
                    (->> (keys frame-sides)
                         (reduce
                          (fn[F k]
                            (assoc F k (xform-recom
                                        (frame-sides k) re-com-xref)))
                          default-frame)))
        (get-adb [:dbg :frame]))

      (udata :slider)
      (let [sval (rgt/atom "0.0")]
        (printchan :SLIDER-INSTRUMENTOR)
        (merge default-frame
               {:top (xform-recom
                      (udata :slider)
                      :m1 sval
                      :oc1 #(do (bar-slider-fn tabid %)
                                (reset! sval (str %)))
                      :oc2 #(do (bar-slider-fn tabid (js/parseFloat %))
                                (reset! sval %)))}))

      (udata :test2)
      (merge default-frame
             {:right [[gap :size "10px"]
                      [v-box :children
                       [[label :label "Select a demo"]
                        [single-dropdown
                         :choices (udata :test2)
                         :on-change #(printchan "Dropdown: " %)
                         :model nil
                         :placeholder "Hi there"
                         :width "100px"]]]]})

      :else default-frame
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
            _ (hmi/app-send msg)
            otspec (async/<! (get-adb [:main :convert-chan]))
            otchart (modal-panel
                     :backdrop-color   "grey"
                     :backdrop-opacity 0.4
                     :child [v-box
                             :gap "10px"
                             :children [[vgl otspec]
                                        [h-box :gap "5px" :justify :end
                                         :children
                                         [[md-circle-icon-button
                                           :md-icon-name "zmdi-close"
                                           :tooltip "Close"
                                           :on-click donefn]]]]])]
        (update-adb [:main :otchart] otchart)
        otchart))))

(defn tab<-> [tabval]
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
             #(do (reset! input "") (reset! output ""))]
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
                              (hmi/app-send msg)
                              (async/<! (get-adb [:main :convert-chan]))))))]
            [md-circle-icon-button
             :md-icon-name "zmdi-caret-up-circle"
             :tooltip "Render in Popup"
             :on-click #(if (= @output "")
                          (reset! alert? true)
                          (let [ch (vis-panel @output process-done)]
                            (go (async/<! ch)
                                (reset! show? true))))]
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
    (printchan render? result)
    (go (async/>! ch result))))


(defmethod user-msg :app-init [msg]
  (update-adb [:main :convert-chan] (async/chan))
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



;;; Startup ============================================================== ;;;

#_(when-let [elem (js/document.querySelector "#app")]
  (hc/add-defaults
   :HEIGHT 400 :WIDTH 450
   :USERDATA {:tab {:id :TID, :label :TLBL, :opts :TOPTS}
              :opts :OPTS
              :vid :VID, :msgop :MSGOP, :session-name :SESSION-NAME}
   :VID RMV, :MSGOP :tabs, :SESSION-NAME "Exploring"
   :TID :expl1, :TLBL #(-> :TID % name cljstr/capitalize)
   :OPTS (hc/default-opts :vgl)
   :TOPTS {:order :row, :eltsper 2 :size "auto"})
  (start :elem elem
         :port js/location.port
         :instrumentor-fn instrumentor))



(comment

  (when-let [elem (js/document.querySelector "#app")]
    (hc/add-defaults
     :HEIGHT 400 :WIDTH 450
     :USERDATA {:tab {:id :TID, :label :TLBL, :opts :TOPTS}
                :opts :OPTS
                :vid :VID, :msgop :MSGOP, :session-name :SESSION-NAME}
     :VID RMV, :MSGOP :tabs, :SESSION-NAME "Exploring"
     :TID :expl1, :TLBL #(-> :TID % name cljstr/capitalize)
     :OPTS (hc/default-opts :vgl)
     :TOPTS {:order :row, :eltsper 2 :size "auto"})
    (start :elem elem
           :port 3000
           :instrumentor-fn instrumentor))

  (js/document.createElement "a")


  )
