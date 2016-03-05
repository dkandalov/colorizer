import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes

import java.awt.Color
import java.awt.Font

import static liveplugin.PluginUtil.*


registerAction("UnSquint", "alt shift meta S") { AnActionEvent event ->
	def editor = currentEditorIn(event.project)
	editor.markupModel.removeAllHighlighters()
}
registerAction("Squint", "alt meta S") { AnActionEvent event ->
	def editor = currentEditorIn(event.project)
	editor.markupModel.removeAllHighlighters()

	def allHighlights = []

	def i = ((EditorEx) editor).getHighlighter().createIterator(0)
	while (!i.atEnd()) {
		allHighlights.add([i.textAttributes, i.start, i.end])
		i.advance()
	}

	// this is to change highlighting for keywords (e.g. "if", "import")
	def documentModel = DocumentMarkupModel.forDocument(editor.document, event.project, false)
	def documentHighlighters = documentModel.getAllHighlighters()
	documentHighlighters.each {
		allHighlights.add([it.textAttributes, it.startOffset, it.endOffset])
	}

	allHighlights.addAll(prefixSpacesHighlights(editor))

	allHighlights = allHighlights.findAll{ it[0] != null }

	for (def highlight : allHighlights) {
		def (textAttributes, start, end) = highlight

		def subText = editor.document.text.substring(start, end)
		def backgroundColor = textAttributes.foregroundColor
		if (backgroundColor == null && !subText.trim().empty) {
			backgroundColor = editor.colorsScheme.defaultForeground
		}
		textAttributes = new TextAttributes(
				textAttributes.foregroundColor,
				backgroundColor,
				textAttributes.effectColor,
				textAttributes.effectType,
				textAttributes.fontType
		)
		editor.markupModel.addRangeHighlighter(start, Math.min(end, editor.document.text.length()), -100, textAttributes, HighlighterTargetArea.EXACT_RANGE)
	}
	show("allHighlights: " + allHighlights.size())


	// TODO collect and then apply text attributes

//	editor.settings.setWhitespacesShown(!editor.settings.whitespacesShown)
//	editor.settings.setInnerWhitespaceShown(!editor.settings.innerWhitespaceShown)
//	editor.settings.setLeadingWhitespaceShown(!editor.settings.leadingWhitespaceShown)

	show(editor.markupModel.allHighlighters.size())
}

def prefixSpacesHighlights(Editor editor) {
	def result = []
	(0..<editor.document.lineCount).each { lineIndex ->
		def lineStartOffset = editor.document.getLineStartOffset(lineIndex)
		def lineEndOffset = editor.document.getLineEndOffset(lineIndex)
		def line = editor.document.text.subSequence(lineStartOffset, lineEndOffset)
		int codeStart = line.chars.findIndexOf { it != " " && it != "\t" }
		if (codeStart != -1) {
			def textAttributes = new TextAttributes(
					Color.red,
					Color.red,
					null,
					EffectType.BOXED,
					Font.PLAIN
			)
			result << [textAttributes, lineStartOffset, lineStartOffset + codeStart]
		}
	}
	result
}

if (!isIdeStartup) show("reloaded")


