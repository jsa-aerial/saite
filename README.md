# saite
Interactive documents with editor support (emacs, vim, sublime, and paredit), graphics and visualization, markdown, and LaTex. 咲いて (in bloom). Built on top of [Hanami](https://github.com/jsa-aerial/hanami) Vega/Vega-Lite library, [CodeMiror](https://codemirror.net), [MathJax](https://www.mathjax.org), amd [Specter](https://www.mathjax.org).

<a href="https://jsa-aerial.github.io/aerial.saite/index.html"><img src="https://github.com/jsa-aerial/saite/blob/master/resources/public/images/in-bloom.png" align="left" hspace="10" vspace="6" alt="saite logo" width="150px"></a>

**Saite** is a Clojure(Script) client/server application for the creation and sharing of interactive documents.  Documents fully support creation of interactive visualizations, coupled with editors, markdown and LaTex.  They may be saved and shared via any number of means, as the external format is simply text. End user creation of documents is highly declarative in form and nature.


Table of Contents
=================

   * [saite](#saite)
      * [Installation](#installation)
         * [Uberjar](#uberjar)
            * [Release V0.5.0 summary:](#release-v050-summary)
            * [Release V0.3.3 summary:](#release-v033-summary)
            * [Obtaining and installing server](#obtaining-and-installing-server)
         * [Library](#library)
      * [Features](#features)
      * [Usage](#usage)
      * [The [&lt;-&gt;] tab](#the---tab)
      * [Tabs](#tabs)
         * [The default Expl1 tab](#the-default-expl1-tab)
         * [User tabs](#user-tabs)

[toc](https://github.com/ekalinin/github-markdown-toc)
# saite


**Saite** is a Clojure(Script) client/server application for the creation and sharing of interactive documents.  Documents fully support creation of interactive visualizations, coupled with editors, markdown and LaTex.  They may be saved and shared via any number of means, as the external format is simply text. End user creation of documents is highly declarative in form and nature.

[Vega-Lite](https://vega.github.io/vega-lite/) (VGL) and/or [Vega](https://vega.github.io/vega/) (VG) specifications. These specifications are declarative and completely specified by _data_ (JSON maps). VGL compiles into the lower level grammar of VG which in turn compiles to a runtime format utilizting lower level runtime environments such as [D3](https://d3js.org/), HTML5 Canvas, and [WebGL](https://github.com/vega/vega-webgl-renderer).

Visualizations are formed from parameterized [templates](https://github.com/jsa-aerial/hanami#templates-substitution-keys-and-transformations) which are recursively transformed into legal VGL or VG specifications. In Saite, creating and transforming these templates is generally done on the server side in typical REPL style development. Transformed templates (with their data or data source) are sent to one or more sessions (brower viewers) for rendering.

In versions 0.2.19+, there is CodeMirror editor support for creating templates on the client side and rendering them via a modal panel popup. There are serveral [issues](https://github.com/jsa-aerial/saite/issues) concerning the expansion of this to support more full notebook oriented documents.

Saite also functions as an example application built with [Hanami](https://github.com/jsa-aerial/hanami). As such it has all the capability of Hanami's _template_ system and recursive transformation of parameterized templates.

Saite also uses of the [tab system](https://github.com/jsa-aerial/hanami#tabs) provided by Hanami for automatic tab construction and updates, plus the application specific tab capabilities of that system. Further, Saite makes use of Hanami's visualization [messaging system](https://github.com/jsa-aerial/hanami#messages), in particular, the `user-msg` multimethod with implementations for the `:app-init` user msg as well as the `:data` msg for streaming data plots/charts.

In addition, Saite also makes use of [header support](https://github.com/jsa-aerial/hanami#header) via the `default-header-fn`. This creates a simple page header giving [session support](https://github.com/jsa-aerial/hanami#sessions): a 'session name' for a session as well as an input area to change the name of the session. Updating is based on session name - all sessions with the same name will get the same updates from the server.


## Installation

### Uberjar

Saite is now a full on quite complete interactive document creation tool.  An over view of an earlier state can be found here: [Aug 29 presentation](https://www.youtube.com/watch?v=3Hx7kbub9YE). Loads of stuff mentioned in the futures there are now implemented, including server side code execution, mixing of server and client side code execution, loading from URLs, full paredit support for editors, automated bulk plot/chart static saves, etc.

#### Release V0.5.0 summary:

* Added full editor panel support to picture frame elements
  - Add any number / combination to :left, :right, :up, and/or :down
  - May be either _live_ or _static_. Latter is for typical code markdown
  - Live editors are fully functional with code execution and may also explicitly update any associated frame visualization
  - Static can be neither focused nor editable
  - Theming works for all these editors
  - Add per tab capabile user defined defaults for editor options (sizing, etc)

* Added automated code 'starter' inserts for
  - Text only (empty) frames (for straight markdown and/or editor panels)
  - CodeMirror editor elements for picture frames
  - Visualization frames with starting default template and data source
  - These also include automatic and automated frame (fid), visualization (vid) and editor (eid) ids.

* Added bulk static image saves
  - Will automatically save all images in a document
  - Saved per tab as `session>docname>tabname>vid(s).png``
  - Supports bulk creation by server or client.
  - Simple fast implementation - no 'headless browsers' or other extras required
  - New default 'chart' option in config.edn for where to save

* Added new example documents:
  - `cm-example-picframes.clj`, showing editor support in picture frames
  - `bulk-vis-save.clj`, showing bulk visualization creation and saving

* Added (fwd) slurping and barfing to strings

* Fix several issues with strings in paredit.

* Added main editor panel default sizing to config.edn

* Added main doc (scroller) area defaul max size for width and height


#### Release V0.3.3 summary:

* Self-installing uberjar, which also is used to run the server

* Uberjar now has an `--update` option that will install changed/new resource files (for example, config.edn) in `~/.saite/Update/<version>`

* Themes are now available for editors (and output areas).  There are 62 available.  New `:theme` key and value in `:editor` section of config.edn.  Also dynamically change via the 'paintbrush` icon in upper right.

* Remember cursor position between tab selections

* Auto center cursor position between tab selections

* `Ctrl-L` for `recenter-top-bottom` of current line

* `Ctrl-X D` for show doc of function

* `Ctrl-X S` for show source of function

* **Breaking Change** : server side `def`s are no longer automatically dereferenced before sending result to client.  This turned out to be more trouble than it may have been worth. Especially when large data results are involved as well as unmarshallable data types (classes and such)

* Load saved docs from URLs (see below for example). So, you can easily publish documents for use by others.

* Emacs editor support for editor buffers.
  - Full ability to configure your key-sequences to functionality (in config.edn `editor` section)

  - Full set of CodeMirror base emacs and full paredit functions exposed for such bindings (see [key bindings](https://github.com/jsa-aerial/saite/blob/master/doc/std-key-maps.md)

  - Vim and Sublime can be specified in the config.edn file

  - The same functions are available to vim and sublime users, but they will need to setup the key configurations to access them.

  - Autocomplete (defaults to Alt-\ key sequence). This 'works' but is not yet fully functional...

  - Full paredit support is available - in particular slurp and barf

* Code execution:
  - On client (with self-hoste ClojureScript)
  - On server (with synchronized evaluations)
  - Mixed code - interweave server code with client code. This mode is especially useful for large computation based visualizations

* **Turnkey** Neanderthal support (for Mac and Linux)

* **Dynamic dependencies**: declare dependencies in your code buffers via the `deps` function. You can use this to pull in your favorite libraries, so that you can then `require` their resources.

______________________________________________________________________________

#### Obtaining and installing server

As indicated, there is now a full self-installing JAR, which will also install the MKL libraries necessary for [Neanderthal](https://clojars.org/uncomplicate/neanderthal) as well as a default config.edn file. For the MKL libs, at the time of this note (11-Feb-2020), Linux 64bit and MacOS 10.11+ 64bit, and  Win10 are supported.

For the self installing JAR, you can grab it with this (note, new versions are coming out fairly often and these versions will be reflected here):

wget http://bioinformatics.bc.edu/~jsa/aerial.aerosaite-0.5.0-standalone.jar

You need Java-8. At present it will **not** run properly on 9+ due to all the non backward compatible changes there (in particular dynamic dependencies do not yet work on 9+). But Java 8 seems to be the platform most (80% I think) JVM users still use. And in any case would be easy to get.

Once you have the uberjar and Java 8, you can **install** with this command:

`java -jar path-to-where-you-downloaded-it/aerial.aerosaite-0.5.0-standalone.jar --install`

You will be asked for the home/install directory with the default `~/.saite`. It is a good idea to take the default - as this is needed for the scripts and other things to work without changes. Optionally you could specify a different location, but then make a _link_ to it as `~/.saite`.  A log of what happens is output to stdout.  MKL libraries for Neanderthal will also be downloaded and installed under the `Libs` directory of the home/install directory.

Optionally (if you have previously installed an earlier version) **update** with this command:

`java -jar path-to-where-you-downloaded-it/aerial.aerosaite-0.5.0-standalone.jar --update`

This will use a manifest file to create a set of updated resources under the version update directory: `~/.saite/Updates<the-version>`.  These resources are _not_ automatically placed in their default locations (for example `~/.saite/config.edn`) because you may have made your own personal changes to such resources. However, it would be a good idea to rename any changed documents and move them.  More important is to merge updated changes in `config.edn` to your version. This is important as there can be several new expected fields for defaults that, if missing, can cause issues.


Once installed or updated **move the downloaded standalone.jar to the home/install directory**, if that was not its download location. The run scripts are setup to expect the jar to be in ~/.saite. There are scripts `linux-runserver` (Linux) script and `mac-runserver` (Mac OS) that are installed. These scripts also setup the requirements for MKL use. To use the scripts the home/install directory should be `~/.saite`, **the jar should be in this directory**, and lastly you will need to `chmod a+x linux-runserver` or `chmod a+x mac-runserver`. Win10 bat file script is not yet available.

You can also run the server with (for example on Linux from a terminal session):

`/usr/bin/nohup java -jar path-to-where-you-downloaded-it/aerial.aerosaite-0.5.0-standalone.jar --port 3000 --repl-port 4100 > start.log &`

As you probably know, `nohup` just keeps the app running if you exit the terminal session. `--port` is where the web server is listening. `--repl-port` is where you could connect emacs / cider, but it uses `nrepl 0.2.13` so you would need something compatible with that. In any case, you don't really need to connect to the nrepl (and I find myself never doing that actually...), especially as you can execute server code from the client.

Connect by pointing your browser at `localhost:3000`. Once in, next click on the 'Upload Document' button (standard icon for this - and all the buttons have tooltips). Then click the URL checkbox. Then put this URL in:

https://raw.githubusercontent.com/jsa-aerial/saite/master/examples/Docs/Scicloj/BosCljMeetup.clj

and click the check/OK button. This will load an updated version of the document I presented at a recent Boston Clojure Meetup. There is expanded walk through commentary in the 'Templates', 'Tabs', and 'Picture Frames' tabs. Additionally some of the commentary (especially in the 'saite' tabs) has been updated. The 'Gallery' tab shows a good collection f various visualizations with full walk through comments in the code.

There is also a simple example document showing Neanderthal and Kixi Stats usage (via dynamic dependencies). URL:

https://raw.githubusercontent.com/jsa-aerial/saite/master/examples/Docs/Test/neanderthal-et-al.clj

All the 'interactive document' tabs have their editor panels closed on load (that's how the doc was saved). You can open and close them with the 'Open Editor Panel' and 'Collapse Editor Panel' buttons. Once open you can use the slider to adjust how much is showing.

The editors are emacs - most of the usual base emacs stuff is available. I haven't actually checked, but if you configure the editor to be `vim` or `sublime` a lot of stuff will still work - including 'key-chords'.  To get a quick overview of the *default* (as from the default config.edn) text manipulation and sexp navigation, various code execution capabilities, and frame visualization capabilities (and their key bindings) click the '?' "Quick Help" button at the upper right. A lot of this is also gone over in the walk through commentary. You can clear output area panels with the 'open circle' button at the left of editor panels (you may need to pull the slider to the right to make this visible if the panel was collapsed).  `Ctrl-X Ctrl-B` will also work.

**Do not** use the editors in the <-> tab for any code execution. They are not intended for that - only for converting between JSON and Clj Vega/Vega-Lite specification code.

To entice you a bit, here is some code snippets (from a real session) showing server and mixed server / client code.

The **key thing** here is to pay attention to which evaluator to use (which key chord). In particular, mixed code, code with a clj form in it, can only be correctly run with Ctrl-X Ctrl-C. If you (accidently or otherwise) use Ctrl-X Ctrl-J (run outer sexp on JVM), Ctrl-X J (run last sexp on JVM), or Ctrl-X Ctrl-E (run last sexp on client), you will get an error saying the clj symbol is not resolvable. That's because it actually doesn't exist - there is no clj operator. Mixed code is dealt with via a re-write transformation where the clj disappears and async messages (to and from the server) and channels for synching are used in the implementation.

To try this, you can simply click the 'Add Interactive Tab' button, then click 'Editor and Output', then click check/OK button. Just select/copy the following and paste into the left editor panel (the right is the repl/output buffer). Pay attention to the comments to make sure you use the 'correct' evaluators...

```Clojure
;;; Place cursor at end of form paren and use Ctrl-X J to run last sexp on
;;; JVM or place cursor in the form and use Ctrl-X Ctrl-J (run outer sexp on
;;; JVM)
(defn roundit [r & {:keys [places] :or {places 4}}]
   (let [n (Math/pow 10.0 places)]
    (-> r (* n) Math/round (/ n))))

;;; Same for this
(defn log2 [x]
  (let [ln2 (Math/log 2)]
    (/ (Math/log x) ln2)))

;;; Now run this by placing cursor in the form (anywhere) and use Ctrl-X  Ctrl-C
;;; (run mixed code) The (clj ...) form will run on the server (during which a
;;; spinner will appear under the open-circle / clear button The results of
;;; that server side computation are then bound to the outer obsdist in
;;; the client. After, you can check both obsdist vars: place cursor at
;;; the end of `obdist`. Use Ctrl-X Ctrl-E to get the client's value and
;;; use Ctrl-X J to get the server's value (they will be equal...)
(def obsdist
  (clj
   (deref
     (def obsdist
       (let [obs [[0 9] [1 78] [2 305] [3 752] [4 1150] [5 1166]
                  [6 899] [7 460] [8 644] [9 533] [10 504]]
             totcnt (->> obs (mapv second) (apply +))
             pdist (map (fn[[k cnt]] [k (double (/ cnt totcnt))]) obs)]
         pdist)))))

;;; Like above, use Ctrl-X Ctrl-C. The body will be run on the server and
;;; then the result will be handed to the client side (take 10 ...) form
(take 10 (clj (mapv #(let [RE (it/KLD (->> obsdist (into {}))
                                      (->> (p/binomial-dist 10 %)
                                        (into {})))
                           REtt (roundit RE)
                           ptt (roundit % :places 2)]
                       {:x % :y RE})
                    (range 0.06 0.98 0.01))))

;;; This one is kind of interesting. Use Ctrl-X Ctrl-C (as before)
;;; Runs (log2 23.4) on server then binds to x on client, then runs
;;; (roundit x) on the server binding that result to y on client. Then
;;; runs (+ x y) in client.
(let [x (clj (log2 23.4))
      y (clj (roundit x))]
  (+ x y))
```
_____________________________________________________________________________



### Library

To install, add the following to your project `:dependencies`:

    [aerial.saite "0.19.15"]

All the features and capabilities mentioned for the uberjar are available from this as well. Except for self contained execution and such.


Typical work flow starts by requiring `aerial.saite.core` and running the `start` function which takes a port. This port is for the websocket messaging. Browsing to this port on localhost will open the viewer.

## Features

* Hanami's parameterized templates with recursive transformations
* Sophisticated tab page structure
  * Uses Hanami's built in tabbing system
  * Uses both 'extension tabs' and 'wrapping functions'
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
* Multiple [named sessions groups]https://github.com/jsa-aerial/hanami#sessions()
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

The `[<->]` tab will be current upon startup. It holds the resources for converting JSON VG/VGL to Clj and Clj VG/VGL (including templates) to JSON. Both areas are code editors [CodeMirror](https://codemirror.net/doc/manual.html). The left editor is where you can type (or more typically paste) a JSON VGL specification. The caret-right button converts to Clj and renders in the right editor pane. Clicking the double (fast-forward) arrow button first compiles the (expected) VGL code to VG and then converts to Clj. The open buttons for each editor clear their respective editor panels.

The caret-left button over the Clj editor area will take a Clj encoded specification, including templates, and convert to the correponding VGL specification, or VG specification if the original Clj corresponded to a Vega spec - including Vega templates.

Lastly the caret-up button over the Clj editor will render the Clj specification in a modal panel popup window. This supports rapid exploration on the client as you can edit the Clj pane and reclick to see the results.


As a first example, the following shows an overlay+detail VGL specification, translated to VG and rendered as Clojure.

![Saite pic 1.1](resources/public/images/vgl-vg-clj.png?raw=true)


Here is an example where we translate a Clj specifications for an area chart, using the hanami template for area charts, over to its JS/JSON Vega-Lite specification.

![Saite pic 1.2](resources/public/images/clj-vgl-json.png?raw=true)


Next we render this specification in a modal panel popup by clicking the caret-up button:

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

![Saite pic 2.1](resources/public/images/simple-scatter-plot.png?raw=true)

Each new rendering will overwrite the content of the tab. Also, the default tab need not be used - just specify a tab id in all your specifications.


### User tabs

Any number of other tabs may be specified, with all manner of text based (hiccup and re-com enabled) areas to picture frames ([frames](https://github.com/jsa-aerial/hanami#picture-frames)) with descriptive areas (top, bottom, left, and right) surrounding visulaizations. There may be any number of visualizations (with or without picture frames) and/or empty frames (textual areas) per tab.

[User meta data](https://github.com/jsa-aerial/hanami#meta-data-and-the-userdata-key) is used to communicate information about such things as which tab to use, the tab's options, whether the template is Vega-Lite or Vega, et. al. This meta data is contained in a map associated with [substitution key](https://github.com/jsa-aerial/hanami#templates-substitution-keys-and-transformations) `:USERDATA` for VGL/VG specification key `:usermeta`. The `:usermeta` key is recognized by VGL/VG and explicitly ignored by their processing. All your templates (or explicit specifications) need to supply `:usermeta` as a key with either explicit values, or more typically (and usefully) a value of `:USERDATA` which the recursive transformation will then transform to a value. For example, here is what the `ht/point-chart` template looks like:

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
 :renderer :RENDERER,
 :mode :MODE}

:SESSION-NAME "Exploring"
:TID :expl1
:TLBL #(-> :TID % name cljstr/capitalize)
:TOPTS {:order :row, :eltsper 2, :size "auto"}

:VID hc/RMV
:MSGOP :tabs
```

The `TLBL` value is an example of a substitution key which is a [function](https://github.com/jsa-aerial/hanami#function-values-for-substitution-keys). Such functions are passed the current substitution map as an argument during recursive transformation. So, in this case, the current tab will get a label that is the capitalized string of the `:TID` value. The `:TOPTS` provides a way of describing the layout of independent visualizations. Visualizations that are _independent_ are those that are separate VGL/VG renderings. So, they constitute different/independent VGL/VG specifications.

All of these values can be changed, either via a call to [hc/update-defaults](https://github.com/jsa-aerial/hanami#templates-and-substitution-keys) or per visualization by explicitly supplying them to the [hc/xform](https://github.com/jsa-aerial/hanami#templates-and-substitution-keys) function. For example, in the following, we specify a new tab `:dists`. The tab label will automatically be set to "Dists" (you could override this with an explicit `:TLBL` k/v):

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

![Saite pic 2.2](resources/public/images/dists-tab-1.png?raw=true)

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

![Saite pic 2.3](resources/public/images/row-auto-2.png?raw=true)

If we add `:TOPTS {:order :col :size "none"}` after the first `:UDATA`, the result will be:

![Saite pic 2.4](resources/public/images/col-auto-2.png?raw=true)

