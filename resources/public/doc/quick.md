
## Editor Keys

* The following are 'control' sequences for editor operations

### Text manipulation

  * `Ctrl-S` : regular expression search. Type in expression, then `return`. Match will be highlighted. Repeat `ctrl-s` to find more examples. Click in editor body to end.

  * `Ctrl-X R` : regular expression replace. Type in expression to replace, then `return`. Type in replacement. Use listed controls to choose which to replace and to stop.

  * `Ctrl-Home` : Move cursor to line 1, character 0

  * `Ctrl-End`  : Move cursor to last line and last character

  * `Ctrl-L`    : Center current position

  * `Ctrl-X D`  : Show JVM engine doc string

  * `Ctrl-X H`  : Show JS engine help string

  * `Ctrl-X S`  : Show source

  * `F9`        : Show hint completions for JVM engine

  * `Ctrl-F9`   : Show hint completions for JS engine

  * `Ctrl-F` : Move forward sexpression

  * `Ctrl-B` : Move backward sexpression

  * `Ctrl-Right` : Forward slurp sexpression

  * `Ctrl-Left`  : Forward barf sexpression

  * `Shift-[an arrow key]` : make selection (for cutting, copying)

  * `Alt-W` : Cut current selection (and delete associated picframe if relevant)

  * `Ctrl-Y` : Yank last cut to cursor position (and insert associated picframe if relevant)

  * `Alt-K` : Cut/kill forward sexpression

  * `Ctrl-/` : Undo last change. Repeat for more.

  * `Ctrl-X Ctrl-B` : Clears the output area


### Code execution

  * `Ctrl-X X`      : Show transformed (compiled) code for mixed code

  * `Ctrl-X Ctrl-E` : Evaluate last sexpression

  * `Ctrl-X Ctrl-C` : Evaluate outer sexpression at cursor (including 'clj' JVM subcode)

  * `Ctrl-X J`      : Evaluate last sexpression _on the JVM_

  * `Ctrl-X Ctrl-J` : Evaluate outer sexpession _on the JVM_

  * `Ctrl-X Ctrl-M` : Evaluate mixed code (synonym for `ctrl-x ctrl-c`)


### Frame Editing and visualization

  * `Ctrl-Alt-T` : Insert shortcut of text only (empty) frame
  * `Ctrl-Alt-V` : Insert shortcut of visualization frame
  * `Ctrl-Alt-C` : Insert shortcut of CodeMirror markdown element
  * `Ctrl-Alt-M` : Insert shortcut of markdown element
  * `Ctrl-Alt-\` : Insert shortcut of LaTex element
  * `Ctrl-Alt-D` : Insert shortcut of tab MD defaults

  * `Ctrl-X Ctrl-I` : Insert frame defined by outer sexpression at cursor
  * `Insert` : synonym for `ctrl-x ctrl-i`

  * `Ctrl-X Ctrl-D` : Delete frame associated with outer sexpression at cursor
  * `Delete` : synonym for `ctrl-x ctrl-d`

  * `Ctrl-X Ctrl-V` : Repaint vis associated with frame at cursor

  * `Ctrl-Alt-W` : Enhanced cut - will remove associated frame if visible. This is now included in standard cut (see above `Alt-W`)
  * `Ctrl-Alt-Y` : Enhanced yank - will insert (in position) associated frame if *not* visible. This is now included in standard yank (see above `Ctrl-Y`)


## Control Bar