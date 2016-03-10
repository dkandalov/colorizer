import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange

import static liveplugin.PluginUtil.*

if (!isIdeStartup) show("reloaded")

registerAction("UnSquint", "alt shift meta S") { AnActionEvent event ->
	def editor = currentEditorIn(event.project)
	editor.markupModel.removeAllHighlighters()
}
registerAction("Squint", "alt meta S") { AnActionEvent event ->
	def editor = currentEditorIn(event.project)
	editor.markupModel.removeAllHighlighters()

	def allHighlights = new HighlightList()
	allHighlights.addAll(editorHighlights(editor))
	allHighlights.addAll(documentHighlights(editor, event.project))
//	allHighlights.addAll(prefixSpacesHighlights(editor))

	for (def highlight : allHighlights) {
		def textAttributes = highlight.textAttributes
		def range = highlight.range

		def text = editor.document.text.substring(range.startOffset, range.endOffset)
		def backgroundColor = textAttributes.foregroundColor
		if (backgroundColor == null && !text.trim().empty) {
			backgroundColor = editor.colorsScheme.defaultForeground
		}
		textAttributes = new TextAttributes(
				textAttributes.foregroundColor,
				backgroundColor,
				textAttributes.effectColor,
				textAttributes.effectType,
				textAttributes.fontType
		)
		editor.markupModel.addRangeHighlighter(
				range.startOffset,
				Math.min(range.endOffset, editor.document.text.length()),
				Integer.MAX_VALUE,
				textAttributes,
				HighlighterTargetArea.EXACT_RANGE
		)
	}
	show("allHighlights: " + allHighlights.size())


	// TODO collect and then apply text attributes

//	editor.settings.setWhitespacesShown(!editor.settings.whitespacesShown)
//	editor.settings.setInnerWhitespaceShown(!editor.settings.innerWhitespaceShown)
//	editor.settings.setLeadingWhitespaceShown(!editor.settings.leadingWhitespaceShown)

	show(editor.markupModel.allHighlighters.size())
}

def editorHighlights(Editor editor) {
	def result = []
	def i = ((EditorEx) editor).getHighlighter().createIterator(0)
	while (!i.atEnd()) {
		result.add(new Highlight(i.textAttributes, i.start, i.end))
		i.advance()
	}
	result.sort(true){ it.range.startOffset }
	result.each{ log(it) }
	result
}

def documentHighlights(Editor editor, Project project) {
	def documentModel = DocumentMarkupModel.forDocument(editor.document, project, false)
	def documentHighlighters = documentModel.getAllHighlighters()
	documentHighlighters.collect {
		new Highlight(it.textAttributes, it.startOffset, it.endOffset)
	}
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
			result.add(new Highlight(textAttributes, lineStartOffset, lineStartOffset + codeStart))
		}
	}
	result
}

class HighlightList implements Iterable<Highlight> {
	private final Set<TextRange> ranges = new HashSet()
	private final List<Highlight> highlightList = new ArrayList<>()

	def addAll(Collection<Highlight> highlights) {
		highlights.each {
			add(it)
		}
	}

	def add(Highlight highlight) {
		if (ranges.contains(highlight.range)) {
			if (!highlight.textAttributes?.empty) {
				highlightList.removeAll{ it.range == highlight.range }
				highlightList.add(highlight)
			}
		} else {
			highlightList.add(highlight)
			ranges.add(highlight.range)
		}
	}

	@Override Iterator<Highlight> iterator() {
		highlightList.iterator()
	}
}

class Highlight {
	final TextAttributes textAttributes
	final TextRange range

	Highlight(TextAttributes textAttributes, int from, int to) {
		this.textAttributes = textAttributes
		this.range = new TextRange(from, to)
	}
}
