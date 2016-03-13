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

	def allHighlights = new HighlightsSet()

//	(editorHighlights(editor) + documentHighlights(editor, event.project))
//		.findAll{ it.range.contains(editor.caretModel.offset) }
//		.each{ show(it) }
//	return

	allHighlights.addAll(editorHighlights(editor))
	allHighlights.addAll(documentHighlights(editor, event.project))
//	allHighlights.addAll(prefixSpacesHighlights(editor))
	allHighlights.addAll(normalTextHighlights(editor, allHighlights))
	allHighlights.addAll(highlightSingleSpaces(editor, allHighlights))
	allHighlights = allHighlights.collectMany{ splitHighlightOnNewLines(editor, it) }

	// TODO collapsed text
	// TODO text with warning

//	allHighlights.each{ log(it.range) }
//	allHighlights
//			.findAll{ it.range.contains(editor.caretModel.offset) }
//			.each{ show(it) }

	for (def highlight : allHighlights) {
		def textAttributes = highlight.textAttributes
		def range = highlight.range

		def backgroundColor = textAttributes.foregroundColor
		if (backgroundColor == null) {
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
	show(editor.markupModel.allHighlighters.size())
}

def Collection<Highlight> splitHighlightOnNewLines(Editor editor, Highlight highlight) {
	def result = []
	def document = editor.document
	while (document.getLineNumber(highlight.range.startOffset) !=
			document.getLineNumber(highlight.range.endOffset)) {
		def endOffset = document.getLineEndOffset(document.getLineNumber(highlight.range.startOffset))
		def headHighLight = new Highlight(
				highlight.textAttributes,
				highlight.range.startOffset, endOffset
		)
		result.add(headHighLight)

		highlight = new Highlight(
				highlight.textAttributes,
				endOffset + 1, highlight.range.endOffset
		)
	}
	result.add(highlight)
	result
}

def Collection<Highlight> editorHighlights(Editor editor) {
	def result = []
	def i = ((EditorEx) editor).getHighlighter().createIterator(0)
	while (!i.atEnd()) {
		result.add(new Highlight(i.textAttributes, i.start, i.end))
		i.advance()
	}
	result.sort(true){ it.range.startOffset }
	result
}

def Collection<Highlight> documentHighlights(Editor editor, Project project) {
	def documentModel = DocumentMarkupModel.forDocument(editor.document, project, false)
	documentModel.getAllHighlighters()
		.findAll { it.textAttributes != null } // because RangeHighlighter textAttributes is nullable
		.collect { new Highlight(it.textAttributes, it.startOffset, it.endOffset) }
}

def Collection<Highlight> normalTextHighlights(Editor editor, HighlightsSet highlightsSet) {
	def result = []
	def text = editor.document.charsSequence
	def defaultTextAttributes = new TextAttributes(
			editor.colorsScheme.defaultForeground,
			editor.colorsScheme.defaultBackground,
			null,
			EffectType.BOXED,
			0
	)

	int wordStart = -1
	for (int i = 0; i < text.length(); i++) {
		char c = text.charAt(i)
		boolean shouldInclude = !c.isWhitespace() && !highlightsSet.containsOffset(i)
		if (wordStart == -1 && shouldInclude) {
			wordStart = i
		} else if (wordStart >= 0 && !shouldInclude) {
			result.add(new Highlight(defaultTextAttributes, wordStart, i))
			wordStart = -1
		}
	}
	if (wordStart >= 0) {
		result.add(new Highlight(defaultTextAttributes, wordStart, text.length()))
	}
	result
}

def Collection<Highlight> highlightSingleSpaces(Editor editor, HighlightsSet highlightsSet) {
	def text = editor.document.charsSequence
	highlightsSet.orderedHighlightPairs()
		.collect { List<Highlight> highlights ->
			def endOffset1 = highlights[0].range.endOffset
			def startOffset2 = highlights[1].range.startOffset
			if (endOffset1 + 1 == startOffset2 && text.charAt(endOffset1) != '\n') {
				new Highlight(highlights[1].textAttributes, endOffset1, endOffset1 + 1)
			} else {
				null
			}
		}
		.findAll{ it != null }
}

def Collection<Highlight> prefixSpacesHighlights(Editor editor) {
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

class HighlightsSet implements Iterable<Highlight> {
	private final Set<TextRange> ranges = new HashSet()
	private final List<Highlight> highlightsList = new ArrayList()

	def addAll(Collection<Highlight> highlights) {
		highlights.each {
			add(it)
		}
	}

	def add(Highlight highlight) {
		if (highlight.textAttributes.empty) {
			return
		}

		if (ranges.contains(highlight.range)) {
			if (!highlight.textAttributes.empty) {
				highlightsList.removeAll{ it.range == highlight.range }
				highlightsList.add(highlight)
			}
		} else {
			highlightsList.add(highlight)
			ranges.add(highlight.range)
		}
	}

	@Override Iterator<Highlight> iterator() {
		highlightsList.iterator()
	}

	List<List<Highlight>> orderedHighlightPairs() {
		highlightsList.sort{ it.range.startOffset }.collate(2, 1, false)
	}

	boolean containsOffset(int offset) {
		highlightsList.any{ it.range.contains(offset) }
	}
}

class Highlight {
	final TextAttributes textAttributes
	final TextRange range

	Highlight(TextAttributes textAttributes, int from, int to) {
		this.textAttributes = textAttributes
		this.range = new TextRange(from, to)
	}

	@Override String toString() {
		"Highlight{" + "textAttributes=" + textAttributes + ", range=" + range + '}'
	}

	@Override boolean equals(o) {
		if (this.is(o)) return true
		if (getClass() != o.class) return false

		Highlight highlight = (Highlight) o

		if (range != highlight.range) return false
		if (textAttributes != highlight.textAttributes) return false

		return true
	}

	@Override int hashCode() {
		int result
		result = (textAttributes != null ? textAttributes.hashCode() : 0)
		result = 31 * result + (range != null ? range.hashCode() : 0)
		return result
	}
}
