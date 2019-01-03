# saite
Exploratory graphics and visualization system. 咲いて (in bloom). Built on top of [Hanami](https://github.com/jsa-aerial/hanami) Vega/Vega-Lite library

<a href="https://jsa-aerial.github.io/aerial.saite/index.html"><img src="https://github.com/jsa-aerial/saite/blob/master/resources/public/images/in-bloom.png" align="left" hspace="10" vspace="6" alt="saite logo" width="150px"></a>

**Saite** is a Clojure(Script) mini "client/server" application for exploratory creation of interactive visualizations based in [Vega-Lite](https://vega.github.io/vega-lite/) (VGL) and/or [Vega](https://vega.github.io/vega/) (VG) specifications. These specifications are declarative and completely specified by _data_ (JSON maps). VGL compiles into the lower level grammar of VG which in turn compiles to a runtime format utilizting lower level runtime environments such as [D3](https://d3js.org/), HTML5 Canvas, and [WebGL](https://github.com/vega/vega-webgl-renderer).

Table of Contents
=================

   * [saite](#saite)
      * [Installation](#installation)
      * [Features](#features)
      * [Usage](#usage)
      * [The [&lt;-&gt;] tab](#the---tab)
      * [Tabs](#tabs)
         * [The default Expl1 tab](#the-default-expl1-tab)
         * [User tabs](#user-tabs)

[toc](https://github.com/ekalinin/github-markdown-toc)
# saite

**Saite** is a Clojure(Script) mini "client/server" application for exploratory creation of interactive visualizations based in [Vega-Lite](https://vega.github.io/vega-lite/) (VGL) and/or [Vega](https://vega.github.io/vega/) (VG) specifications. These specifications are declarative and completely specified by _data_ (JSON maps). VGL compiles into the lower level grammar of VG which in turn compiles to a runtime format utilizting lower level runtime environments such as [D3](https://d3js.org/), HTML5 Canvas, and [WebGL](https://github.com/vega/vega-webgl-renderer).

Typical work flow starts by requiring `aerial.saite.core` and running the `start` function which takes a port. This port is for the websocket messaging. Browsing to this port on localhost will open the viewer.

Visualizations are formed from parameterized templates (see [Hanami](https://github.com/jsa-aerial/hanami)) which are recursively transformed into legal VGL or VG specifications. In Saite, creating and transforming these templates is done on the server side in typical REPL style development. Generally, transformed templates (with their data or data source) are sent to one or more sessions (brower viewers) for rendering.

Saite also functions as an example application built with [Hanami](https://github.com/jsa-aerial/hanami). As such it has all the capability of Hanami's _template_ system and recursive transformation of parameterized templates.

Saite also uses of the _tab system_ provided by Hanami for automatic tab construction and updates, plus the application specific tab capabilities of that system. Further, Saite makes use of Hanami's visualization messaging system, in particular, the `user-msg` multimethod with implementations for the `:app-init` user msg as well as the `:data` msg for streaming data plots/charts.

In addition, Saite also makes use of `default-header-fn` provided by Hanami. This creates a simple page header giving the 'session name' for a session as well as an input area to change the name of the session. Updating is based on session name - all sessions with the same name will get the updates from the server.


## Installation

To install, add the following to your project `:dependencies`:

    [aerial.saite "0.3.1"]


## Features

* Hanami's parameterized templates with recursive transformations
* Simple tab page structure
  * Uses Hanami's built in tabbing system
  * Each tab can have arbitrary number of _independent_ visulaizations
  * Tab layout is configurable - row/col and number of elements/(row/col)
  * Tabs can have names that make sense to you
* Initial tab for converting VGL to Clj and VGL to VG to Clj
  * CodeMirror enabled editors for each panel
  * Clj panel can render the spec in a modal panel popup
  * Clj can also make use of templates in conversion and renderings
  * useful for creating starting point specs that you turn into templates
  * useful for merging VGL with VG only capabilities
  * Visualization templates have a default tab, with name "Expl"
  * Any number of additional tabs can be created and used
* Multiple named based sessions
  * Each session can be given a name (the default is 'Exploring')
  * An input area is available to change the name
  * All sessions with the same name get updates for that name
  * Sessions with different _names_ are independent
* Data streaming
  * realtime charts
  * can work for multiple tabs/charts simultaneously
  * **NOTE** not operational in version 0.1.0


## Usage

```Clojure
(ns exploring1.examples
  (:require [aerial.saite.core :as as]
            [aerial.hanami.common :as hc]
            [aerial.hanami.templates :as ht]
            [aerial.hanami.core :as hmi]
            ...
            )
  ...)

(as/start 3000)
```

Browse to `localhost:3000` and you will see an initial session page:

![Saite pic 1](resources/public/images/start-page.png?raw=true)


## The `[<->]` tab

The `[<->]` tab will be current upon startup. It holds the resources for converting JSON VGL to Clj. The left area is where you can type (or more typically paste) a JSON VGL specification. The dark arrow button converts to Clj and renders in the right area. The light arrow first compiles to VG and then converts to Clj. The open button clears both panels.

For example, the following shows an example overlay+detail VGL specification, translated to VG and rendered as Clojure.

![Saite pic 1.1](resources/public/images/vgl-vg-clj.png?raw=true)

You can also translate Clj specifications, including hanami templates, over to their JS/JSON Vega and Vega-Lite specifications. For example, the following shows an area chart as template and translated to the corresponding Vega-Lite JSON specification.

![Saite pic 1.2](resources/public/images/clj-vgl-json.png?raw=true)


And further, you can quickly test render any Clj specification (including templates) in a modal panel popup by clicking the caret-up button. For example, we render the previoius area chart:

![Saite pic 1.3](resources/public/images/clj-modal-render.png?raw=true)



## Tabs

Saite incorporates and uses Hanami's default tab system to provide 'pages' for organizing your document and visual explorations (see [Hanami](https://github.com/jsa-aerial/hanami))

### The default `Expl1` tab

The following will create an example plot in the default tab

```Clojure
(->> (hc/xform ht/point-chart
       :UDATA "data/cars.json"
       :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")
     hmi/sv!)
```

![Saite pic 1.4](resources/public/images/simple-scatter-plot.png?raw=true)

Each new rendering will overwrite the content of the tab. Also, the default tab need not be used - just specify a tab id in all your specifications.


### User tabs

Any number of other tabs may be specified, with all manner of text based (hiccup and re-com enabled) areas to picture frames ([frames](https://github.com/jsa-aerial/hanami#picture-frames)) with descriptive areas (top, bottom, left, and right) surrounding visulaizations. There may be any number of visualizations (with or without picture frames) and/or empty frames (textual areas) per tab.

User meta data is used to communicate information about such things as which tab to use, the tab's options, whether the template is VegaLite or Vega, et. al. This meta data is contained in a map associated with _substitution key_ `:USERDATA` for VGL/VG specification key `:usermeta` (see [Hanami](https://github.com/jsa-aerial/hanami) for details on substitution keys and templates and transformations). The `:usermeta` key is recognized by VGL/VG and explicitly ignored by their processing. All your templates (or explicit specifications) need to supply `:usermeta` as a key with either explicit values, or more typically (and usefully) a value of `:USERDATA` which the recursive transformation will then transform to a value. For example, here is what the `ht/point-chart` template looks like:

```Clojure
(def point-chart
  {:usermeta :USERDATA
   :title  :TITLE
   :height :HEIGHT
   :width :WIDTH
   :background :BACKGROUND
   :selection :SELECTION
   :data data-options
   :transform :TRANSFORM
   :mark {:type "circle", :size :MSIZE}
   :encoding :ENCODING})
```

Saite sets a variety of defaults for `:USERDATA` as follows:

```Clojure
:USERDATA
{:tab {:id :TID, :label :TLBL, :opts :TOPTS},
 :opts :OPTS,
 :vid :VID,
 :msgop :MSGOP,
 :session-name :SESSION-NAME}

:OPTS
{:export {:png true, :svg false},
 :renderer "canvas",
 :mode "vega-lite"}

:SESSION-NAME "Exploring"
:TID :expl1
:TLBL #(-> :TID % name cljstr/capitalize)
:TOPTS {:order :row, :eltsper 2, :size "auto"}

:VID hc/RMV
:MSGOP :tabs
```

The `TLBL` value is an example of a substitution key which is a function. Such functions are passed the current substitution map as an argument during recursive transformation. So, in this case, the current tab will get a label that is the capitalized string of the `:TID` value. The `:TOPTS` provides a way of describing the layout of independent visualizations. Visualizations that are _independent_ are those that are separate VGL/VG renderings. So, they constitute different/independent VGL/VG specifications.

All of these values can be changed, either via an explicit call to `hc/add-defaults` or implicitly per visualization by supplying them to the `hc/xform` function. For example, in the following, we specify a new tab `:dists`. The tab label will automatically be set to "Dists" (you could override this with an explicit `:TLBL` k/v):

```Clojure
(->>
 [(hc/xform ht/layer-chart
    :TID :dists
    :TITLE "A Real (obvserved) distribution with incorrect sample mean"
    :LAYER [(hc/xform ht/bar-layer :XTITLE "Count" :YTITLE "Probability")
            (hc/xform ht/xrule-layer :AGG "mean")]
    :DATA (mapv (fn[[x y]] {:x x :y y :m 5.7}) obsdist))

  (hc/xform ht/layer-chart
    :TID :dists
    :TITLE "The same distribution with correct weighted mean"
    :LAYER [(hc/xform ht/bar-layer :XTITLE "Count" :YTITLE "Probability")
            (hc/xform ht/xrule-layer :X "m")]
    :DATA (mapv (fn[[x y]] {:x x :y y :m 5.7}) obsdist))]
 hmi/sv!)
```

![Saite pic 1.2](resources/public/images/dists-tab-1.png?raw=true)

Exploring this a bit further let's look at four independent plots and charts. The actual visualizations are not particularly related - this is just to show the grid layout aspect (which is actually a direct Hanami capability).

First, taking the defaults indicated above, the folowing lays out a row ordered 2X2 grid, where the first two charts are the first row, and the bar charts are the second row.


```Clojure
(->>
 (mapv #(apply hc/xform %)
       [[ht/point-chart
         :UDATA "data/cars.json"
         :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin"]
        (let [data (->> (range 0.005 0.999 0.001)
                        (mapv (fn[p] {:x p, :y (- (m/log2 p)) :col "SI"})))]
          [ht/layer-chart
           :TITLE "Self Information (unexpectedness)"
           :LAYER [(hc/xform ht/xrule-layer :AGG "mean")
                   (hc/xform ht/line-layer
                     :XTITLE "Probability of event" :YTITLE "-log(p)")]
           :DATA data])
        [ht/bar-chart
         :UDATA "data/seattle-weather.csv" :TOOLTIP RMV
         :X "date" :XTYPE "ordinal" :XUNIT "month"
         :Y "precipitation" :YAGG "mean"]
        [ht/layer-chart
         :UDATA "data/seattle-weather.csv"
         :LAYER [(hc/xform ht/bar-layer
                   :TOOLTIP RMV
                   :X "date" :XTYPE "ordinal" :XUNIT "month"
                   :Y "precipitation" :YAGG "mean"
                   :SELECTION {:brush {:type "interval", :encodings ["x"]}}
                   :OPACITY {:condition {:selection "brush", :value 1}, :value 0.7})
                 (hc/xform ht/yrule-layer
                   :TRANSFORM [{:filter {:selection "brush"}}]
                   :Y "precipitation" :AGG "mean" :YRL-COLOR "firebrick")]]])
 hmi/sv!)
```

![Saite pic 1.3](resources/public/images/row-auto-2.png?raw=true)

If we add `:TOPTS {:order :col :size "none"}` after the first `:UDATA`, the result will be:

![Saite pic 1.4](resources/public/images/col-auto-2.png?raw=true)

