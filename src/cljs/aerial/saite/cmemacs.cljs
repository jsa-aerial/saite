(ns aerial.saite.cmemacs
  (:require
   [cljsjs.codemirror]
   [cljsjs.codemirror.keymap.emacs]))


(def kill-region ((js->clj CodeMirror.keyMap.emacs) "Ctrl-W"))
(def kill-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-K"))
(def kill-ring-save ((js->clj CodeMirror.keyMap.emacs) "Alt-W"))

(def yank ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Y"))
(def yank-pop ((js->clj CodeMirror.keyMap.emacs) "Alt-Y"))

(def set-mark ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Space"))
(def select-all ((js->clj CodeMirror.keyMap.emacs) "Ctrl-X H"))

(def forward-char ((js->clj CodeMirror.keyMap.emacs) "Ctrl-F"))
(def backward-char ((js->clj CodeMirror.keyMap.emacs) "Ctrl-B"))

(def delete-forward-char ((js->clj CodeMirror.keyMap.emacs) "Ctrl-D"))
(def delete-backward-char ((js->clj CodeMirror.keyMap.emacs) "Backspace"))

(def forward-word ((js->clj CodeMirror.keyMap.emacs) "Alt-F"))
(def backward-word ((js->clj CodeMirror.keyMap.emacs) "Alt-B"))
(def forward-kill-word ((js->clj CodeMirror.keyMap.emacs) "Alt-D"))
(def backward-kill-word ((js->clj CodeMirror.keyMap.emacs) "Alt-Backspace"))

(def next-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-N"))
(def previous-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-P"))

(def beginning-of-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-A"))
(def end-of-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-E"))

(def scroll-down ((js->clj CodeMirror.keyMap.emacs) "Alt-V"))
(def page-up scroll-down)
(def scroll-up ((js->clj CodeMirror.keyMap.emacs) "Ctrl-V"))
(def page-down scroll-up)

(def backward-paragraph ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Up"))
(def forward-paragraph ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Down"))

(def backward-sentence ((js->clj CodeMirror.keyMap.emacs) "Alt-A"))
(def forward-sentence ((js->clj CodeMirror.keyMap.emacs) "Alt-E"))
(def kill-sentence ((js->clj CodeMirror.keyMap.emacs) "Alt-K"))

(def kill-sexp ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-K"))
(def kill-back-sexp ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-Backspace"))

(def forward-sexp ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-F"))
(def backward-sexp ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-B"))

(def mark-sexp ((js->clj CodeMirror.keyMap.emacs) "Shift-Ctrl-Alt-2"))
(def transpose-sexps ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-T"))
(def backward-up-list ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-U"))

(def just-one-space ((js->clj CodeMirror.keyMap.emacs) "Alt-Space"))
(def open-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-O"))
(def transpose-chars ((js->clj CodeMirror.keyMap.emacs) "Ctrl-T"))

(def capitalize-word ((js->clj CodeMirror.keyMap.emacs) "Alt-C"))
(def uppercase-word ((js->clj CodeMirror.keyMap.emacs) "Alt-U"))
(def downcase-word ((js->clj CodeMirror.keyMap.emacs) "Alt-L"))

(def toggle-comment ((js->clj CodeMirror.keyMap.emacs) "Alt-;"))

(def undo ((js->clj CodeMirror.keyMap.emacs) "Ctrl-/"))
(def repeat-undo ((js->clj CodeMirror.keyMap.emacs) "Shift-Ctrl--"))

(def go-doc-start ((js->clj CodeMirror.keyMap.emacs) "Shift-Alt-,"))
(def go-doc-end ((js->clj CodeMirror.keyMap.emacs) "Shift-Alt-."))
(def goto-line ((js->clj CodeMirror.keyMap.emacs) "Alt-G G"))

(def search-forward ((js->clj CodeMirror.keyMap.emacs) "Ctrl-S"))
(def search-backward ((js->clj CodeMirror.keyMap.emacs) "Ctrl-R"))
(def query-replace ((js->clj CodeMirror.keyMap.emacs) "Shift-Alt-5"))

(def newline-indent ((js->clj CodeMirror.keyMap.emacs) "Enter"))
(def electric-newline-indent ((js->clj CodeMirror.keyMap.emacs) "Ctrl-J"))
(def indent-auto ((js->clj CodeMirror.keyMap.emacs) "Tab"))
(def indent-selection ((js->clj CodeMirror.keyMap.emacs) "Ctrl-X Tab"))
(def exchange-point-mark ((js->clj CodeMirror.keyMap.emacs) "Ctrl-X Ctrl-X"))

(def save-doc ((js->clj CodeMirror.keyMap.emacs) "Ctrl-X Ctrl-S"))
(def open-doc ((js->clj CodeMirror.keyMap.emacs) "Ctrl-X F"))

(def autocomplete ((js->clj CodeMirror.keyMap.emacs) "Alt-/"))

(def keyboard-quit ((js->clj CodeMirror.keyMap.emacs) "Ctrl-G"))




(def emacs-kb-xref
  (->>
   (mapv vector
         '[kill-region kill-line kill-ring-save
           yank yank-pop  set-mark select-all
           forward-char backward-char
           delete-forward-char delete-backward-char
           forward-word backward-word forward-kill-word backward-kill-word
           next-line previous-line
           beginning-of-line end-of-line
           scroll-down scroll-up page-up page-down
           backward-paragraph forward-paragraph
           backward-sentence forward-sentence
           kill-sentence kill-sexp kill-back-sexp
           forward-sexp backward-sexp
           mark-sexp transpose-sexps backward-up-list
           just-one-space open-line transpose-chars
           capitalize-word uppercase-word downcase-word
           toggle-comment
           undo repeat-undo
           go-doc-start go-doc-end goto-line
           search-forward search-backward query-replace
           newline-indent electric-newline-indent indent-auto indent-selection
           exchange-point-mark
           save-doc open-doc
           autocomplete
           keyboard-quit]
         [kill-region kill-line kill-ring-save
          yank yank-pop  set-mark select-all
          forward-char backward-char
          delete-forward-char delete-backward-char
          forward-word backward-word forward-kill-word backward-kill-word
          next-line previous-line
          beginning-of-line end-of-line
          scroll-down scroll-up page-up page-down
          backward-paragraph forward-paragraph
          backward-sentence forward-sentence
          kill-sentence kill-sexp kill-back-sexp
          forward-sexp backward-sexp
          mark-sexp transpose-sexps backward-up-list
          just-one-space open-line transpose-chars
          capitalize-word uppercase-word downcase-word
          toggle-comment
          undo repeat-undo
          go-doc-start go-doc-end goto-line
          search-forward search-backward query-replace
          newline-indent electric-newline-indent indent-auto indent-selection
          exchange-point-mark
          save-doc open-doc
          autocomplete
          keyboard-quit])
   (into {})))




(def std-keymap
  {"Ctrl-W" kill-region
   "Ctrl-K" kill-line
   "Alt-W"  kill-ring-save

   "Ctrl-Y" yank
   "Alt-Y"  yank-pop

   "Ctrl-Space" set-mark
   "Ctrl-X H"   select-all

   "Ctrl-F" forward-char
   "Ctrl-B" backward-char

   "Ctrl-D"    delete-forward-char
   "Backspace" delete-backward-char

   "Alt-F"         forward-word
   "Alt-B"         backward-word
   "Alt-D"         forward-kill-word
   "Alt-Backspace" backward-kill-word

   "Ctrl-N" next-line
   "Ctrl-P" previous-line

   "Ctrl-A" beginning-of-line
   "Ctrl-E" end-of-line

   "Alt-V"    scroll-down
   "Ctrl-V"   scroll-up
   "PageUp"   page-up
   "PageDown" page-down

   "Ctrl-Up"   backward-paragraph
   "Ctrl-Down" forward-paragraph

   "Alt-A" backward-sentence
   "Alt-E" forward-sentence
   "Alt-K" kill-sentence

   "Ctrl-Alt-K"         kill-sexp
   "Ctrl-Alt-Backspace" kill-back-sexp

   "Ctrl-Alt-F" forward-sexp
   "Ctrl-Alt-B" backward-sexp

   "Shift-Ctrl-Alt-2" mark-sexp
   "Ctrl-Alt-T"       transpose-sexps
   "Ctrl-Alt-U"       backward-up-list

   "Alt-Space" just-one-space
   "Ctrl-O"    open-line
   "Ctrl-T"    transpose-chars

   "Alt-C" capitalize-word
   "Alt-U" uppercase-word
   "Alt-L" downcase-word

   "Alt-;" toggle-comment

   "Ctrl-/"       undo
   "Shift-Ctrl--" repeat-undo
   "Ctrl-Z"       undo
   "Cmd-Z"        repeat-undo

   "Shift-Alt-," go-doc-start
   "Shift-Alt-." go-doc-end
   "Alt-G G"     goto-line

   "Ctrl-S"      search-forward
   "Ctrl-R"      search-backward
   "Shift-Alt-5" query-replace

   "Enter"         newline-indent
   "Ctrl-J"        electric-newline-indent
   "Tab"           indent-auto
   "Ctrl-X Tab"    indent-selection
   "Ctrl-X Ctrl-X" exchange-point-mark

   "Ctrl-X Ctrl-S" save-doc
   "Ctrl-X F"      open-doc

   "Alt-/" autocomplete

   "Ctrl-G" keyboard-quit
   })

