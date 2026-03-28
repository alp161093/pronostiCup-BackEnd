package com.pronosticup.backend.pronostics.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pronosticup.backend.pronostics.entity.Pronostic;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Service
public class PronosticPdfService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(26, 59, 105));
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 13, Font.BOLD, Color.WHITE);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(26, 59, 105));
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(60, 60, 60));
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font TABLE_VALUE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Color HEADER_BLUE = new Color(40, 84, 132);
    private static final Color LIGHT_BORDER = new Color(210, 220, 232);
    private static final Color LIGHT_BG = new Color(247, 249, 252);

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    /**
     * Genero un PDF legible del pronóstico con fase de grupos y eliminatorias.
     */
    public byte[] generatePronosticPdf(Pronostic pronostic, String documentTitle) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 28, 28, 28, 28);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            addHeader(document, pronostic, documentTitle);
            //salto de pagina para que sea de presentacion
            document.newPage();
            addGroupStageSection(document, pronostic);
            addKnockoutSection(document, pronostic);

            document.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF del pronóstico", e);
        }
    }

    /**
     * Pinto la cabecera general del comprobante.
     */
    private void addHeader(Document document, Pronostic pronostic, String documentTitle) throws DocumentException {
        Paragraph title = new Paragraph("PronostiCup", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph(documentTitle, SECTION_FONT);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(16f);
        document.add(subtitle);

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(20f);
        infoTable.setWidths(new float[]{1.2f, 2.5f});

        addInfoRow(infoTable, "Liga", safe(pronostic.getLeagueName()));
        addInfoRow(infoTable, "Torneo", safe(pronostic.getTournament()));
        addInfoRow(infoTable, "Alias", safe(pronostic.getPronosticAlias()));
        addInfoRow(infoTable, "Creado", formatInstant(pronostic.getCreatedAt()));
        addInfoRow(infoTable, "Actualizado", formatInstant(pronostic.getUpdatedAt()));

        document.add(infoTable);
    }

    /**
     * Pinto la fase de grupos dejando exactamente dos grupos por página.
     */
    @SuppressWarnings("unchecked")
    private void addGroupStageSection(Document document, Pronostic pronostic) throws DocumentException {
        Map<String, Object> groupStage = pronostic.getGroupStage();
        if (groupStage == null || groupStage.isEmpty()) {
            return;
        }

        Map<String, List<Map<String, Object>>> standingsByGroup =
                castGroupMap(groupStage.get("standingsByGroup"));

        Map<String, List<Map<String, Object>>> matchesByGroup =
                castGroupMap(groupStage.get("matchesByGroup"));

        List<String> orderedGroups = new ArrayList<>(standingsByGroup.keySet());
        orderedGroups.sort(String::compareTo);

        int groupsInCurrentPage = 0;
        boolean firstGroupsPage = true;

        for (String groupKey : orderedGroups) {

            if (groupsInCurrentPage == 0) {
                if (!firstGroupsPage) {
                    document.newPage();
                }

                Paragraph sectionTitle = new Paragraph("Fase de grupos", SECTION_FONT);
                sectionTitle.setSpacingBefore(2f);
                sectionTitle.setSpacingAfter(10f);
                document.add(sectionTitle);

                firstGroupsPage = false;
            }

            addGroupBlock(document, groupKey, matchesByGroup.get(groupKey), standingsByGroup.get(groupKey));
            groupsInCurrentPage++;

            if (groupsInCurrentPage == 2) {
                groupsInCurrentPage = 0;
            }
        }
    }

    /**
     * Pinto un grupo con el bloque de partidos y el bloque de clasificación.
     */
    private void addGroupBlock(
            Document document,
            String groupKey,
            List<Map<String, Object>> matches,
            List<Map<String, Object>> standings
    ) throws DocumentException {

        PdfPTable groupHeader = new PdfPTable(1);
        groupHeader.setWidthPercentage(100);
        PdfPCell headerCell = buildHeaderCell("Grupo " + groupKey);
        groupHeader.addCell(headerCell);
        groupHeader.setSpacingBefore(8f);
        groupHeader.setSpacingAfter(6f);
        document.add(groupHeader);

        PdfPTable contentTable = new PdfPTable(2);
        contentTable.setWidthPercentage(100);
        contentTable.setWidths(new float[]{2.2f, 1.1f});
        contentTable.setSpacingAfter(12f);

        PdfPCell matchesCell = new PdfPCell();
        matchesCell.setBorderColor(LIGHT_BORDER);
        matchesCell.setBackgroundColor(Color.WHITE);
        matchesCell.setPadding(8f);
        matchesCell.addElement(buildSubsectionTable("Partidos"));
        matchesCell.addElement(buildMatchesTable(matches));

        PdfPCell standingsCell = new PdfPCell();
        standingsCell.setBorderColor(LIGHT_BORDER);
        standingsCell.setBackgroundColor(Color.WHITE);
        standingsCell.setPadding(8f);
        standingsCell.addElement(buildSubsectionTable("Clasificación"));
        standingsCell.addElement(buildStandingsTable(standings));

        contentTable.addCell(matchesCell);
        contentTable.addCell(standingsCell);

        document.add(contentTable);
    }

    /**
     * Pinto las eliminatorias dejando cada ronda en una página independiente.
     */
    @SuppressWarnings("unchecked")
    private void addKnockoutSection(Document document, Pronostic pronostic) throws DocumentException {
        Map<String, Object> knockouts = pronostic.getKnockouts();
        if (knockouts == null || knockouts.isEmpty()) {
            return;
        }

        List<Map<String, Object>> koRounds = (List<Map<String, Object>>) knockouts.get("koRounds");
        Map<String, Map<String, Object>> koMatches = castNestedMap(knockouts.get("koMatches"));

        if (koRounds == null || koRounds.isEmpty()) {
            return;
        }

        for (Map<String, Object> round : koRounds) {
            document.newPage();

            Paragraph sectionTitle = new Paragraph("Eliminatorias", SECTION_FONT);
            sectionTitle.setSpacingBefore(2f);
            sectionTitle.setSpacingAfter(10f);
            document.add(sectionTitle);

            String title = safe(round.get("title"));
            List<String> matchIds = castStringList(round.get("matchIds"));

            addKnockoutRound(document, title, matchIds, koMatches);
        }
    }

    /**
     * Pinto una ronda KO con todos sus partidos.
     */
    private void addKnockoutRound(
            Document document,
            String roundTitle,
            List<String> matchIds,
            Map<String, Map<String, Object>> koMatches
    ) throws DocumentException {

        PdfPTable roundHeader = new PdfPTable(1);
        roundHeader.setWidthPercentage(100);
        roundHeader.setSpacingBefore(8f);
        roundHeader.setSpacingAfter(6f);
        roundHeader.addCell(buildHeaderCell(roundTitle));
        document.add(roundHeader);

        PdfPTable roundTable = new PdfPTable(1);
        roundTable.setWidthPercentage(100);
        roundTable.setSpacingAfter(8f);

        for (String matchId : matchIds) {
            Map<String, Object> match = koMatches.get(matchId);
            if (match == null) {
                continue;
            }
            roundTable.addCell(buildKnockoutMatchCell(match));
        }

        document.add(roundTable);
    }

    /**
     * Construyo una tabla simple con los partidos del grupo.
     */
    private PdfPTable buildMatchesTable(List<Map<String, Object>> matches) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6f);
        table.setWidths(new float[]{2.4f, 1.0f, 2.4f, 1.4f});

        addTableHeader(table, "Local");
        addTableHeader(table, "Res.");
        addTableHeader(table, "Visitante");
        addTableHeader(table, "Fecha");

        if (matches == null || matches.isEmpty()) {
            addEmptyRow(table, 4, "No hay partidos");
            return table;
        }

        for (Map<String, Object> match : matches) {
            table.addCell(buildValueCell(safe(match.get("homeName"))));
            table.addCell(buildValueCell(buildScoreText(match.get("homeGoals"), match.get("awayGoals"))));
            table.addCell(buildValueCell(safe(match.get("awayName"))));
            table.addCell(buildValueCell(formatDate(match.get("date"))));
        }

        return table;
    }

    /**
     * Construyo una tabla simple con la clasificación del grupo.
     */
    private PdfPTable buildStandingsTable(List<Map<String, Object>> standings) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6f);
        table.setWidths(new float[]{2.3f, 0.8f, 0.8f, 0.8f, 0.8f});

        addTableHeader(table, "Equipo");
        addTableHeader(table, "Pts");
        addTableHeader(table, "GF");
        addTableHeader(table, "GC");
        addTableHeader(table, "DG");

        if (standings == null || standings.isEmpty()) {
            addEmptyRow(table, 5, "No hay clasificación");
            return table;
        }

        for (Map<String, Object> row : standings) {
            table.addCell(buildValueCell(safe(row.get("team"))));
            table.addCell(buildCenteredValueCell(safe(row.get("pts"))));
            table.addCell(buildCenteredValueCell(safe(row.get("gf"))));
            table.addCell(buildCenteredValueCell(safe(row.get("gc"))));
            table.addCell(buildCenteredValueCell(safe(row.get("gd"))));
        }

        return table;
    }

    /**
     * Construyo una celda visual para un partido de eliminatoria.
     */
    @SuppressWarnings("unchecked")
    private PdfPCell buildKnockoutMatchCell(Map<String, Object> match) throws DocumentException {
        Map<String, Object> home = (Map<String, Object>) match.get("home");
        Map<String, Object> away = (Map<String, Object>) match.get("away");
        Map<String, Object> winner = (Map<String, Object>) match.get("winner");

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.6f, 1.0f, 2.6f});

        PdfPCell homeCell = buildValueCell(safe(home != null ? home.get("team") : null));
        homeCell.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell scoreCell = buildCenteredValueCell(buildScoreText(match.get("homeGoals"), match.get("awayGoals")));
        scoreCell.setBackgroundColor(LIGHT_BG);

        PdfPCell awayCell = buildValueCell(safe(away != null ? away.get("team") : null));
        awayCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(homeCell);
        table.addCell(scoreCell);
        table.addCell(awayCell);

        PdfPCell wrapper = new PdfPCell();
        wrapper.setPadding(8f);
        wrapper.setBorderColor(LIGHT_BORDER);
        wrapper.setBackgroundColor(Color.WHITE);
        wrapper.addElement(table);

        if (winner != null && winner.get("team") != null) {
            Paragraph winnerText = new Paragraph("Pasa: " + safe(winner.get("team")), VALUE_FONT);
            winnerText.setSpacingBefore(4f);
            winnerText.setAlignment(Element.ALIGN_CENTER);
            wrapper.addElement(winnerText);
        }

        return wrapper;
    }

    /**
     * Creo la cabecera azul de cada bloque.
     */
    private PdfPCell buildHeaderCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, SUBTITLE_FONT));
        cell.setBackgroundColor(HEADER_BLUE);
        cell.setBorderColor(HEADER_BLUE);
        cell.setPadding(8f);
        return cell;
    }

    /**
     * Creo una mini cabecera interna para subsecciones.
     */
    private PdfPTable buildSubsectionTable(String title) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(title, LABEL_FONT));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(4f);
        table.addCell(cell);
        return table;
    }

    /**
     * Añado una fila de información a la tabla de cabecera.
     */
    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorderColor(LIGHT_BORDER);
        labelCell.setBackgroundColor(LIGHT_BG);
        labelCell.setPadding(6f);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, VALUE_FONT));
        valueCell.setBorderColor(LIGHT_BORDER);
        valueCell.setPadding(6f);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    /**
     * Añado una cabecera de columna estándar.
     */
    private void addTableHeader(PdfPTable table, String title) {
        PdfPCell cell = new PdfPCell(new Phrase(title, TABLE_HEADER_FONT));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(new Color(235, 239, 245));
        cell.setBorderColor(LIGHT_BORDER);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    /**
     * Añado una fila vacía cuando no hay datos.
     */
    private void addEmptyRow(PdfPTable table, int colspan, String text) {
        PdfPCell empty = new PdfPCell(new Phrase(text, TABLE_VALUE_FONT));
        empty.setColspan(colspan);
        empty.setHorizontalAlignment(Element.ALIGN_CENTER);
        empty.setBorderColor(LIGHT_BORDER);
        empty.setPadding(8f);
        table.addCell(empty);
    }

    /**
     * Construyo una celda de valor normal.
     */
    private PdfPCell buildValueCell(String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, TABLE_VALUE_FONT));
        cell.setBorderColor(LIGHT_BORDER);
        cell.setPadding(6f);
        return cell;
    }

    /**
     * Construyo una celda de valor centrado.
     */
    private PdfPCell buildCenteredValueCell(String value) {
        PdfPCell cell = buildValueCell(value);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    /**
     * Formateo el resultado del partido mostrando guiones si todavía no hay goles.
     */
    private String buildScoreText(Object homeGoals, Object awayGoals) {
        String home = homeGoals == null ? "-" : String.valueOf(homeGoals);
        String away = awayGoals == null ? "-" : String.valueOf(awayGoals);
        return home + " - " + away;
    }

    /**
     * Formateo una fecha ISO del pronóstico a un texto legible.
     */
    private String formatDate(Object rawDate) {
        if (rawDate == null) {
            return "-";
        }

        try {
            return DATE_FORMATTER.format(Instant.parse(String.valueOf(rawDate)));
        } catch (Exception e) {
            return String.valueOf(rawDate);
        }
    }

    /**
     * Formateo un Instant del documento a un texto legible.
     */
    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return DATE_FORMATTER.format(instant);
    }

    /**
     * Devuelvo un texto seguro para no propagar nulls al PDF.
     */
    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    /**
     * Convierto standingsByGroup o matchesByGroup a un mapa tipado.
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> castGroupMap(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return Collections.emptyMap();
        }

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof List<?> rawList) {
                List<Map<String, Object>> parsedList = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof Map<?, ?> mapItem) {
                        parsedList.add((Map<String, Object>) mapItem);
                    }
                }
                result.put(key, parsedList);
            }
        }
        return result;
    }

    /**
     * Convierto koMatches a un mapa tipado por id de partido.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> castNestedMap(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> valueMap) {
                result.put(String.valueOf(entry.getKey()), (Map<String, Object>) valueMap);
            }
        }
        return result;
    }

    /**
     * Convierto la lista de ids de partidos KO a una lista de strings.
     */
    private List<String> castStringList(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            result.add(String.valueOf(item));
        }
        return result;
    }
}
