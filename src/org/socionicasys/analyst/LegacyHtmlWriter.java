package org.socionicasys.analyst;

import org.socionicasys.analyst.model.AData;
import org.socionicasys.analyst.types.Sociotype;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.text.*;

public class LegacyHtmlWriter extends SwingWorker<Void, Void> {
	private static final Logger logger = LoggerFactory.getLogger(LegacyHtmlWriter.class);

	private static final int HEADER_PROGRESS = 20;
	private static final int PREPARATION_PROGRESS = 20;
	private static final int TEXT_PROGRESS = 40;
	private static final int REPORT_PROGRESS = 20;

	private final ADocument document;
	private final File outputFile;
	private final AnalystWindow analystWindow;

	private enum EventType {
		LINE_BREAK,
		SECTION_START,
		SECTION_END,
		NEW_ROW,
		BOLD_START,
		BOLD_END,
		ITALIC_START,
		ITALIC_END
	}

	private static final class DocumentFlowEvent {
		private final EventType type;
		private final int offset;
		private final int sectionNo;
		private final String style;
		private final String comment;

		private DocumentFlowEvent(EventType type, int offset, String style, String comment, int sectionNo) {
			this.offset = offset;
			this.type = type;
			this.style = style;
			this.comment = comment == null ? null : comment.replaceAll("\n", "<br/>");
			this.sectionNo = sectionNo;
		}

		public int getOffset() {
			return offset;
		}

		public EventType getType() {
			return type;
		}

		public String getStyle() {
			return style;
		}

		public String getComment() {
			return comment;
		}

		public int getSectionNo() {
			return sectionNo;
		}
	}

	/**
	 * Делает возможной сортировку массива из {@link DocumentFlowEvent}-ов.
	 * Сравнение происходит только по позиции (offset).
	 */
	@SuppressWarnings("serial")
	private static final class DocumentFlowEventComparator implements Comparator<DocumentFlowEvent>, Serializable {
		@Override
		public int compare(DocumentFlowEvent o1, DocumentFlowEvent o2) {
			return Integer.valueOf(o1.getOffset()).compareTo(o2.getOffset());
		}
	}

	private static final class RDStack {
		private final List<String> styleStack;
		private final Map<Integer, Integer> positionMap;

		private RDStack() {
			styleStack = new ArrayList<String>();
			positionMap = new HashMap<Integer, Integer>();
		}

		public void push(final int handle, final String element) {
			styleStack.add(element);
			positionMap.put(handle, styleStack.size() - 1);
		}

		public void delete(final int handle) {
			int position = positionMap.get(handle);
			styleStack.remove(position);
			positionMap.remove(handle);
			for (Map.Entry<Integer, Integer> entry : positionMap.entrySet()) {
				int key = entry.getKey();
				int value = entry.getValue();
				if (value > position) {
					positionMap.put(key, value - 1);
				}
			}
		}

		public String getCurrentStyle() {
			if (isEmpty()) {
				return null;
			}
			return styleStack.get(styleStack.size() - 1);
		}

		public boolean isEmpty() {
			return styleStack.isEmpty();
		}
	}

	public LegacyHtmlWriter(AnalystWindow analystWindow, ADocument document, File outputFile) {
		this.document = document;
		this.outputFile = outputFile;
		this.analystWindow = analystWindow;
	}

	@Override
	protected Void doInBackground() throws BadLocationException, IOException {
		Writer writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile),
					LegacyHtmlFormat.FILE_ENCODING));
			writeDocument(writer);
			document.setAssociatedFile(outputFile);
		} catch (BadLocationException e) {
			logger.error("Incorrect document position while saving document", e);
			throw e;
		} catch (IOException e) {
			logger.error("IO error while saving document", e);
			throw e;
		} finally {
			if (writer != null) {
				writer.close();
			}
		}

		return null;
	}

	private void writeDocument(Writer writer) throws IOException, BadLocationException {
		setProgress(0);

		//writing the header
		writer.write(String.format(
		"<!DOCTYPE HTML PUBLIC W3C//DTD HTML 4.01//EN http://www.w3.org/TR/html4/strict.dtd>" + 
		"<meta http-equiv=\"Content-Type\" content=\"text/html charset=UTF-8\"/><html>" +
		"<head>" +
		"<title>New Document</title>" +
		"	<style>			body 	{font-size:14px;color:black;font-family:Lucida Sans Unicode,Lucida Grande,sans-serif}" +
		"			h1		{font-size:36px; font-weight:bold; color: #001400}" +
		"			h2		{font-size:24px; font-weight:bold; color: #001400}" +
		"			th		{font-size:18px;font-weight:bold; background-color: #009879; color: #f7f7f7}" +
		"			small	{font-size:14px;font-weight:bold;color: #222222}" +
		".styled-table {" +
		"    border-collapse: collapse;" +
		"    margin: 25px 0;" +
		"    font-size: 14px;" +
		"    font-family: sans-serif;" +
		"    min-width: 400px;" +
		"    box-shadow: 0 0 20px rgba(0, 0, 0, 0.15);" +
		"}" +
		".styled-table th," +
		".styled-table td {" +
		"    padding: 12px 15px;" +
		"}" +
		".styled-table tbody tr {" +
		"    border-bottom: 1px solid #dddddd;" +
		"}" +
		"" +
		".styled-table tbody tr:nth-of-type(even) {" +
		"    background-color: #f3f3f3;" +
		"}" +
		"" +
		".styled-table tbody tr:last-of-type {" +
		"    border-bottom: 2px solid #009879;" +
		"}" +
		"	</style>" +
		"</head>", 
			LegacyHtmlFormat.FILE_ENCODING,
			document.getProperty(Document.TitleProperty)
		));

		//document title
		writer.write(
		"<table border=\"0\" cellpadding=\"10\" cellspacing=\"0\" style=\"background-color:#023020; border-collapse:collapse; width:100%\">" +
		"	<tbody>" +
		"		<tr>" +
		"			<td><span style=\"font-size:48px\"> <style=\"height:84px; width:100px\" /><span style=\"color:#ffffff\"><span style=\"font-family:Lucida Sans Unicode,Lucida Grande,sans-serif\"><strong><sup>SCS Typing Protocol</sup></strong></span></span></span></td>" +
		"		</tr>" +
		"	</tbody>" +
		"</table>"		
		);
		//document header
		writer.write(String.format(
			"<hr />" +
			"<p><span style=\"font-family:Lucida Sans Unicode,Lucida Grande,sans-serif; color: #001400\"><span style=\"font-size:24px\"><strong>Subject</strong>: %s </span></span></p>" +
			"<p><span style=\"font-family:Lucida Sans Unicode,Lucida Grande,sans-serif; color: #001400\"><span style=\"font-size:24px\"><strong>Typist(s)</strong>: %s </span></span></p>" +
			"<p><span style=\"font-family:Lucida Sans Unicode,Lucida Grande,sans-serif; color: #001400\"><span style=\"font-size:24px\"><strong>Date</strong>: %s </span></span></p>",
			document.getProperty(ADocument.CLIENT_PROPERTY),
			document.getProperty(ADocument.EXPERT_PROPERTY),
			document.getProperty(ADocument.DATE_PROPERTY)
		));

		//  writing the color legend
		writer.write(
		"<hr />" +
		"<h2><span style=\"font-size:24px\"><strong>Color Legend</strong></span></h2>" +
		"<hr />" +
		"<ul>" +
		"	<li><span style=\"font-size:20px\"><span style=\"font-family:Tahoma,Geneva,sans-serif\"><span style=\"color:#000000\"><span style=\"background-color:#f4cccc\">Unclear Passage</span></span></span></span></li>" +
		"	<li><span style=\"font-size:20px\"><span style=\"font-family:Tahoma,Geneva,sans-serif\"><span style=\"color:#000000\"><span style=\"background-color:#fce5cd\">Sign Information</span></span></span></span></li>" +
		"	<li><span style=\"font-size:20px\"><span style=\"font-family:Tahoma,Geneva,sans-serif\"><span style=\"color:#000000\"><span style=\"background-color:#fff2cc\">Low-Dimensionality</span></span></span></span></li>" +
		"	<li><span style=\"font-size:20px\"><span style=\"font-family:Tahoma,Geneva,sans-serif\"><span style=\"color:#000000\"><span style=\"background-color:#d9ead3\">High-Dimensionality</span></span></span></span></li>" +
		"	<li><span style=\"font-size:20px\"><span style=\"font-family:Tahoma,Geneva,sans-serif\"><span style=\"color:#000000\"><span style=\"background-color:#d0e0e3\">Function Dichotomy</span></span></span></span></li>" +
		"	<li><span style=\"font-size:20px\"><span style=\"font-family:Tahoma,Geneva,sans-serif\"><span style=\"color:#000000\"><span style=\"background-color:#d9d2e9\">Block Information</span></span></span></span></li>" +
		"	<li><span style=\"font-size:20px\"><span style=\"font-family:Tahoma,Geneva,sans-serif\"><span style=\"color:#000000\"><span style=\"background-color:#d5a6bd\">Other</span></span></span></span></li>" +
		"</ul>" +
		"\n" +
		"<hr />" +
		"	<p>&nbsp;</p>" +
		"	<h2><span style=\"font-size:24px\"><strong>SSS Resources:</strong></span></h2>" +
		"<hr />" +
		"	<p><span style=\"font-size:20px\">General Information: <a href=\"https://drive.google.com/drive/u/0/folders/12pxUhsUT_IRQAomf2Mbt72nZHNJNCuSc\">Here</a></span></p>" +
		"	<p><span style=\"font-size:20px\">Numbered List of Indicators: <a href=\"https://docs.google.com/document/d/1_FISl-e_GDk2k9vAzA2D2GHLBTOHSM4dPmG-_AF1Vm4/edit\">Here</a></span></p>" +
		"	<p><span style=\"font-size:20px\">Don&#39;t understand your analysis? Click&nbsp;<a href=\"https://docs.google.com/document/d/1W-nBU51mMH2mUW1nD4Ia_2H7PeLDdxtEQmPEaaLGmZ8/edit\">Here</a></span></p>" +
			"<hr />"
		);

		setProgress(HEADER_PROGRESS);

		//document content
		writer.write(
			"<br/>\n\n" +
			"<h1>ANALYSIS</h1>" +
			"<hr /> \n" +
			"<table title=\"protocol\" border=\"1\" width=\"100%\" class=\"styled-table\" style=\"border-collapse: collapse;\">\n" +
			"<tr>\n" +
			"    <th width=\"60%\">Questions &amp; Responses</th>\n" +
			"    <th width=\"40%\">Typist Remarks</th>\n" +
			"</tr>\n" +
			"<tr>\n" +
			"    <td>"
		);

		// PREPARING
		List<DocumentFlowEvent> flowEvents = new ArrayList<DocumentFlowEvent>();
		Collection<Position> lineBreaks = new ArrayList<Position>();
		for (int i = 0; i < document.getLength(); i++) {
			if (document.getText(i, 1).equals("\n")) {
				lineBreaks.add(document.createPosition(i));
			}
		}

		List<DocumentSection> sections = new ArrayList<DocumentSection>(document.getADataMap().keySet());
		Collections.sort(sections);
		for (int i = 0; i < sections.size(); i++) {
			DocumentSection section = sections.get(i);
			AData data = document.getADataMap().get(section);
			if (data.isValid()) {
				flowEvents.add(new DocumentFlowEvent(
					EventType.SECTION_START,
					section.getStartOffset(),
					getHTMLStyleForAData(data),
					String.format("{%d: %s} %s\n", i + 1, data.toString(), data.getComment()),
					i + 1)
				);
				flowEvents.add(new DocumentFlowEvent(
					EventType.SECTION_END,
					section.getEndOffset(),
					getHTMLStyleForAData(data),
					data.getComment(),
					i + 1)
				);
			}
		}

		Element rootElem = document.getDefaultRootElement();
		MutableAttributeSet boldAttribute = new SimpleAttributeSet();
		StyleConstants.setBold(boldAttribute, true);
		MutableAttributeSet italicAttribute = new SimpleAttributeSet();
		StyleConstants.setItalic(italicAttribute, true);
		for (int parIndex = 0; parIndex < rootElem.getElementCount(); parIndex++) {
			Element parElem = rootElem.getElement(parIndex);
			for (int i = 0; i < parElem.getElementCount(); i++) {
				Element e = parElem.getElement(i);
				int elemStart = e.getStartOffset();
				int elemEnd = e.getEndOffset();
				AttributeSet attrs = e.getAttributes();
				if (attrs.containsAttributes(boldAttribute)) {
					flowEvents.add(new DocumentFlowEvent(EventType.BOLD_START,
						elemStart, null, null, 0));
					flowEvents.add(new DocumentFlowEvent(EventType.BOLD_END,
						elemEnd, null, null, 0));
				}
				if (attrs.containsAttributes(italicAttribute)) {
					flowEvents.add(new DocumentFlowEvent(EventType.ITALIC_START,
						elemStart, null, null, 0));
					flowEvents.add(new DocumentFlowEvent(EventType.ITALIC_END,
						elemEnd, null, null, 0));
				}
			}
		}
		for (Position position : lineBreaks) {
			int lb = position.getOffset();
			boolean replaceBreak = false;
			if (!flowEvents.isEmpty()) {
				DocumentFlowEvent prevEvent = flowEvents.get(flowEvents.size() - 1);
				if (prevEvent.getType() == EventType.LINE_BREAK &&
					prevEvent.getOffset() == lb - 1) {
					// Заменяем два идущих подряд LINE_BREAK на NEW_ROW
					replaceBreak = true;
				}
			}
			if (replaceBreak) {
				flowEvents.set(flowEvents.size() - 1,
					new DocumentFlowEvent(EventType.NEW_ROW,
						lb - 1, null, null, 0));
			} else {
				flowEvents.add(new DocumentFlowEvent(
					EventType.LINE_BREAK, lb, null, null, 0));
			}
		}
		Collections.sort(flowEvents, new DocumentFlowEventComparator());

		if (!flowEvents.isEmpty() && (flowEvents.get(flowEvents.size() - 1).getType() != EventType.NEW_ROW)) {
			flowEvents.add(new DocumentFlowEvent(
				EventType.NEW_ROW, document.getEndPosition().getOffset() - 1,
				null, null, 0));
		}

		setProgress(HEADER_PROGRESS + PREPARATION_PROGRESS);

		// write contents
		RDStack stack = new RDStack();
		if (flowEvents != null && !flowEvents.isEmpty()) {
			int pos1 = 0;
			StringBuilder analysis = new StringBuilder();
			for (int z = 0; z < flowEvents.size(); z++) {
				DocumentFlowEvent event = flowEvents.get(z);
				EventType eventType = event.getType();
				int pos0 = pos1;
				pos1 = event.getOffset();

				setProgress(HEADER_PROGRESS + PREPARATION_PROGRESS + TEXT_PROGRESS * z / flowEvents.size());

				//writing text
				writer.write(document.getText(pos0, pos1 - pos0));

				// writing text remainder from last event to the end of the document
				if (z == flowEvents.size() - 1) {
					int finish = document.getLength();
					if (finish > pos1) {
						writer.write(document.getText(pos1, finish - pos1));
					}
					eventType = EventType.NEW_ROW;
				}

				//analyzing event and generating mark-up
				int sectionNo;
				switch (eventType) {
				case SECTION_START:
					sectionNo = event.getSectionNo();
					if (!stack.isEmpty()) {
						writer.write(" </span>");
					}
					writer.write(String.format("<small>[%d|</small><span style=%s>", sectionNo, event.getStyle()));
					stack.push(sectionNo, event.getStyle());
					analysis.append(event.getComment());
					break;

				case SECTION_END:
					if (!stack.isEmpty()) {
						sectionNo = event.getSectionNo();
						writer.write(String.format("</span><small>|%d]</small>", sectionNo));
						stack.delete(sectionNo);
						if (!stack.isEmpty()) {
							writer.write(String.format("<span style=%s>", stack.getCurrentStyle()));
						}
					}
					break;

				case BOLD_START:
					writer.write("<b>");
					break;

				case BOLD_END:
					writer.write("</b>");
					break;

				case ITALIC_START:
					writer.write("<i>");
					break;

				case ITALIC_END:
					writer.write("</i>");
					break;

				case NEW_ROW:
					if (!stack.isEmpty()) {
						writer.write("</span>");
					}
					writer.write(String.format("</td>\n<td>%s</td>", analysis));
					analysis = new StringBuilder();
					if (z != flowEvents.size() - 1) {
						writer.write("\n</tr>\n<tr>\n<td>");
					}
					if (!stack.isEmpty()) {
						writer.write(String.format("<span style=%s>", stack.getCurrentStyle()));
					}
					break;

				case LINE_BREAK:
					writer.write("<br/>");
					break;
				}
			}
		}
		// если в документе нет разметки - просто пишем текст в левый столбец таблицы
		else {
			writer.write(document.getText(0, document.getLength()));
			writer.write("</td><td></td>");
		}

		writer.write("</tr>\n</table>\n");
		//if not generating report
		setProgress(HEADER_PROGRESS + PREPARATION_PROGRESS + TEXT_PROGRESS + REPORT_PROGRESS);

		// if generating report
		writer.write(
			"<br/>" +
			"<h1> RESULTS </h1>" +
			"<hr />"
		);
		writer.write(analystWindow.getNavigeTree().getReport());
		writeMissMatchReport(writer);

		writer.write(String.format(
			"<br/>" +
			"This is a Type Identification Protocol generated by the School of System Socionics, modified by the West Team. <br/>" +
			"© School of System Socionics, Kyiv.<br/>" +
			"http://socionicasys.org\n" +
			"</body>\n" +
			"</html>\n"
		));

		setProgress(100);
	}

	private static String getHTMLStyleForAData(AData data) {
		if (data.getAspect().equals(AData.DOUBT)) {
			return "background-color:#f4cccc";
		}
		StringBuilder res = new StringBuilder("\"");
		boolean unstyled = true;
		String dimension = data.getDimension();
		if (Arrays.asList(AData.D1, AData.D2, AData.ODNOMERNOST, AData.MALOMERNOST).contains(dimension)) {
			res.append("background-color:#fff2cc;");
			unstyled = false;
		} else if (Arrays.asList(AData.D3, AData.D4, AData.MNOGOMERNOST).contains(dimension)) {
			// противный зеленый
			res.append("background-color:#d9ead3;");
			unstyled = false;
		}
		if (data.getSign() != null) {
			res.append("background-color:#fce5cd;");
			unstyled = false;
		}
		if (data.getFD() != null) {
			res.append("background-color:#d0e0e3;");
			unstyled = false;
		}

		if (data.getBlocks() != null) {
			res.append("background-color:#d9d2e9;");
			unstyled = false;
		}
		//Если не задан другой стиль, то будет этот стиль
		if (unstyled) {
			res.append("background-color:#d5a6bd");
		}
		res.append('\"');
		return res.toString();
	}

	private void writeMissMatchReport(Writer writer) throws IOException {
		if (!document.getADataMap().isEmpty()) {
			writer.write(
				"<br/>" +
				"<h2> Correlation to Socionics Type </h2>" +
				"<hr/>" +
				"The table below serves to determine the most likely Socionics type of the subject.<br/>" +
				"For calculation of the table's fields the following algorithm was used. Each of the marked up passages of the text is checked for accordance with the type model of the 16 types. If the passage accords with a TIM model, then the Accord field for that model is incremented by 1. In case of discordance, the respective Discord field gets incremented by 1. <br/><br/>" +
				"The column “Correlation Factor” shows the normalized Correlation Factor which is calculated for each type according to the formula:<br/>" +
				"<code> Correlation Factor = NORM<small style=\"vertical-align:sub;color:black\">100</small>(Accord/Discord)</code><br/>" +
				"This method is used to the most likely type, but does not list the likelihood of the subject being any respective type. <br/><br/>" +
				"<table title=\"TIM analysis\" border=\"1\" width=\"80%\" class=\"styled-table\" style=\"border-collapse: collapse\">" +
				"<tr>" +
				"	<th width=\"40%\">Sociotype</th>" +
				"	<th width=\"20%\">Accord</th>" +
				"	<th width=\"20%\">Discord</th>" +
				"	<th width=\"20%\">Correlation Factor</th>" +
				"</tr>"
			);
			for (Sociotype sociotype : Sociotype.values()) {
				MatchMissItem matchMissItem = document.getMatchMissModel().get(sociotype);
				writer.write(String.format(
					"<tr>\n" +
					"	<td style=\"font-weight:bold\">%s</td>\n" +
					"		<td align=\"center\">%s </td>\n" +
					"		<td align=\"center\">%s </td>\n" +
					"		<td align=\"center\"> %2.0f </td>\n" +
					"</tr>\n",
					sociotype,
					matchMissItem.getMatchCount(),
					matchMissItem.getMissCount(),
					100.0f * matchMissItem.getScaledCoefficient()
				));
			}
			writer.write("</table>");
		} else {
			writer.write("<br/><h2> Impossible to decide a type </h2><br/>");
		}
	}
}
