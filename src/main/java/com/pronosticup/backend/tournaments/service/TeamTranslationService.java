package com.pronosticup.backend.tournaments.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TeamTranslationService {

    private static final String UNDEFINED_TEAM_NAME = "Por definir";

    private static final Map<String, String> TEAM_TRANSLATIONS = createTranslations();

    /**
     * Recorro el payload de standings y traduzco los nombres de los equipos.
     * Devuelvo una copia modificable para no tocar el mapa original recibido.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> translateStandingsPayload(Map<String, Object> originalPayload) {
        log.info("Iniciando traduccion");
        Map<String, Object> translatedPayload = deepCopyMap(originalPayload);

        Object standingsObject = translatedPayload.get("standings");
        if (!(standingsObject instanceof List<?> standingsList)) {
            return translatedPayload;
        }

        for (Object standingObject : standingsList) {
            if (!(standingObject instanceof Map<?, ?> standingMapRaw)) {
                continue;
            }

            Map<String, Object> standingMap = (Map<String, Object>) standingMapRaw;
            Object tableObject = standingMap.get("table");

            if (!(tableObject instanceof List<?> tableList)) {
                continue;
            }

            for (Object rowObject : tableList) {
                if (!(rowObject instanceof Map<?, ?> rowMapRaw)) {
                    continue;
                }

                Map<String, Object> rowMap = (Map<String, Object>) rowMapRaw;
                translateTeamMap(rowMap.get("team"));
            }
        }

        translateSeasonWinnerIfPresent(translatedPayload);
        log.info("Fin traduccion");
        return translatedPayload;
    }

    /**
     * Recorro el payload de partidos y traduzco los equipos de cada match.
     * También traduzco el winner de season cuando exista.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> translateMatchesPayload(Map<String, Object> originalPayload) {
        Map<String, Object> translatedPayload = deepCopyMap(originalPayload);

        Object matchesObject = translatedPayload.get("matches");
        if (!(matchesObject instanceof List<?> matchesList)) {
            translateSeasonWinnerIfPresent(translatedPayload);
            return translatedPayload;
        }

        for (Object matchObject : matchesList) {
            if (!(matchObject instanceof Map<?, ?> matchMapRaw)) {
                continue;
            }

            Map<String, Object> matchMap = (Map<String, Object>) matchMapRaw;
            translateTeamMap(matchMap.get("homeTeam"));
            translateTeamMap(matchMap.get("awayTeam"));
            translateSeasonWinnerInsideMatchIfPresent(matchMap);
        }

        translateSeasonWinnerIfPresent(translatedPayload);

        return translatedPayload;
    }

    /**
     * Traduzco un objeto team y dejo shortName con el mismo valor que name.
     * Si no hay nombre, no rompo nada y dejo el valor actual.
     */
    @SuppressWarnings("unchecked")
    private void translateTeamMap(Object teamObject) {
        if (!(teamObject instanceof Map<?, ?> teamMapRaw)) {
            return;
        }

        Map<String, Object> teamMap = (Map<String, Object>) teamMapRaw;

        Object nameObject = teamMap.get("name");
        if (!(nameObject instanceof String originalName) || isBlank(originalName)) {
            return;
        }

        String translatedName = translateTeamName(originalName);

        teamMap.put("name", translatedName);
        teamMap.put("shortName", translatedName);
    }

    /**
     * Intento traducir el nombre del equipo usando el diccionario.
     * Si no encuentro traducción, devuelvo el nombre original y dejo trazado un warning.
     */
    private String translateTeamName(String originalName) {
        if (isBlank(originalName)) {
            return UNDEFINED_TEAM_NAME;
        }

        String directTranslation = TEAM_TRANSLATIONS.get(originalName);
        if (directTranslation != null) {
            return directTranslation;
        }

        String normalizedName = normalizeKey(originalName);
        String normalizedTranslation = TEAM_TRANSLATIONS.get(normalizedName);
        if (normalizedTranslation != null) {
            return normalizedTranslation;
        }

        log.warn("No encuentro traducción para teamName='{}'", originalName);
        return originalName;
    }

    /**
     * Traduzco el winner que puede venir dentro de payload.season.winner.
     */
    @SuppressWarnings("unchecked")
    private void translateSeasonWinnerIfPresent(Map<String, Object> payload) {
        Object seasonObject = payload.get("season");
        if (!(seasonObject instanceof Map<?, ?> seasonMapRaw)) {
            return;
        }

        Map<String, Object> seasonMap = (Map<String, Object>) seasonMapRaw;
        translateTeamMap(seasonMap.get("winner"));
    }

    /**
     * Traduzco el winner que puede venir dentro de cada match.season.winner.
     */
    @SuppressWarnings("unchecked")
    private void translateSeasonWinnerInsideMatchIfPresent(Map<String, Object> matchMap) {
        Object seasonObject = matchMap.get("season");
        if (!(seasonObject instanceof Map<?, ?> seasonMapRaw)) {
            return;
        }

        Map<String, Object> seasonMap = (Map<String, Object>) seasonMapRaw;
        translateTeamMap(seasonMap.get("winner"));
    }

    /**
     * Creo una copia profunda básica de mapas y listas para poder modificar el payload con seguridad.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new HashMap<>();

        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }

        return copy;
    }

    /**
     * Copio recursivamente mapas y listas.
     * Los valores simples se reutilizan porque son inmutables o no necesitan clonado aquí.
     */
    @SuppressWarnings("unchecked")
    private Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nestedCopy = new HashMap<>();

            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                nestedCopy.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
            }

            return nestedCopy;
        }

        if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .map(this::deepCopyValue)
                    .toList();
        }

        return value;
    }

    /**
     * Normalizo la clave para soportar búsquedas algo más tolerantes.
     */
    private String normalizeKey(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized.trim().toLowerCase();
    }

    /**
     * Compruebo si el texto viene vacío o solo con espacios.
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Construyo el diccionario EN -> ES.
     * Guardo también variantes normalizadas para cubrir diferencias leves del proveedor.
     */
    private static Map<String, String> createTranslations() {
        Map<String, String> translations = new HashMap<>();

        addTranslation(translations, "Afghanistan", "Afganistán");
        addTranslation(translations, "Albania", "Albania");
        addTranslation(translations, "Germany", "Alemania");
        addTranslation(translations, "Algeria", "Argelia");
        addTranslation(translations, "Andorra", "Andorra");
        addTranslation(translations, "Angola", "Angola");
        addTranslation(translations, "Anguilla", "Anguila");
        addTranslation(translations, "Antarctica", "Antártida");
        addTranslation(translations, "Antigua and Barbuda", "Antigua y Barbuda");
        addTranslation(translations, "Netherlands Antilles", "Antillas Neerlandesas");
        addTranslation(translations, "Saudi Arabia", "Arabia Saudita");
        addTranslation(translations, "Argentina", "Argentina");
        addTranslation(translations, "Armenia", "Armenia");
        addTranslation(translations, "Aruba", "Aruba");
        addTranslation(translations, "Australia", "Australia");
        addTranslation(translations, "Austria", "Austria");
        addTranslation(translations, "Azerbaijan", "Azerbaiyán");
        addTranslation(translations, "Belgium", "Bélgica");
        addTranslation(translations, "Bahamas", "Bahamas");
        addTranslation(translations, "Bahrain", "Baréin");
        addTranslation(translations, "Bangladesh", "Bangladés");
        addTranslation(translations, "Barbados", "Barbados");
        addTranslation(translations, "Belize", "Belice");
        addTranslation(translations, "Benin", "Benín");
        addTranslation(translations, "Bhutan", "Bután");
        addTranslation(translations, "Belarus", "Bielorrusia");
        addTranslation(translations, "Myanmar", "Birmania");
        addTranslation(translations, "Bolivia", "Bolivia");
        addTranslation(translations, "Bosnia and Herzegovina", "Bosnia y Herzegovina");
        addTranslation(translations, "Botswana", "Botsuana");
        addTranslation(translations, "Brazil", "Brasil");
        addTranslation(translations, "Brunei", "Brunéi");
        addTranslation(translations, "Bulgaria", "Bulgaria");
        addTranslation(translations, "Burkina Faso", "Burkina Faso");
        addTranslation(translations, "Burundi", "Burundi");
        addTranslation(translations, "Cape Verde", "Cabo Verde");
        addTranslation(translations, "Cape Verde Islands", "Cabo Verde");
        addTranslation(translations, "Cambodia", "Camboya");
        addTranslation(translations, "Cameroon", "Camerún");
        addTranslation(translations, "Canada", "Canadá");
        addTranslation(translations, "Chad", "Chad");
        addTranslation(translations, "Chile", "Chile");
        addTranslation(translations, "China", "China");
        addTranslation(translations, "Cyprus", "Chipre");
        addTranslation(translations, "Vatican City State", "Ciudad del Vaticano");
        addTranslation(translations, "Colombia", "Colombia");
        addTranslation(translations, "Comoros", "Comoras");
        addTranslation(translations, "Congo", "Congo");
        addTranslation(translations, "North Korea", "Corea del Norte");
        addTranslation(translations, "South Korea", "Corea del Sur");
        addTranslation(translations, "Korea Republic", "Corea del Sur");
        addTranslation(translations, "Ivory Coast", "Costa de Marfil");
        addTranslation(translations, "Costa Rica", "Costa Rica");
        addTranslation(translations, "Croatia", "Croacia");
        addTranslation(translations, "Cuba", "Cuba");
        addTranslation(translations, "Denmark", "Dinamarca");
        addTranslation(translations, "Dominica", "Dominica");
        addTranslation(translations, "Ecuador", "Ecuador");
        addTranslation(translations, "Egypt", "Egipto");
        addTranslation(translations, "El Salvador", "El Salvador");
        addTranslation(translations, "United Arab Emirates", "Emiratos Árabes Unidos");
        addTranslation(translations, "Eritrea", "Eritrea");
        addTranslation(translations, "Slovakia", "Eslovaquia");
        addTranslation(translations, "Slovenia", "Eslovenia");
        addTranslation(translations, "Spain", "España");
        addTranslation(translations, "United States of America", "Estados Unidos");
        addTranslation(translations, "United States", "Estados Unidos");
        addTranslation(translations, "USA", "Estados Unidos");
        addTranslation(translations, "Estonia", "Estonia");
        addTranslation(translations, "Ethiopia", "Etiopía");
        addTranslation(translations, "Philippines", "Filipinas");
        addTranslation(translations, "Finland", "Finlandia");
        addTranslation(translations, "Fiji", "Fiyi");
        addTranslation(translations, "France", "Francia");
        addTranslation(translations, "Gabon", "Gabón");
        addTranslation(translations, "Gambia", "Gambia");
        addTranslation(translations, "Georgia", "Georgia");
        addTranslation(translations, "Ghana", "Ghana");
        addTranslation(translations, "Gibraltar", "Gibraltar");
        addTranslation(translations, "Grenada", "Granada");
        addTranslation(translations, "Greece", "Grecia");
        addTranslation(translations, "Greenland", "Groenlandia");
        addTranslation(translations, "Guadeloupe", "Guadalupe");
        addTranslation(translations, "Guam", "Guam");
        addTranslation(translations, "Guatemala", "Guatemala");
        addTranslation(translations, "French Guiana", "Guayana Francesa");
        addTranslation(translations, "Guernsey", "Guernsey");
        addTranslation(translations, "Guinea", "Guinea");
        addTranslation(translations, "Equatorial Guinea", "Guinea Ecuatorial");
        addTranslation(translations, "Guinea-Bissau", "Guinea-Bisáu");
        addTranslation(translations, "Guyana", "Guyana");
        addTranslation(translations, "Haiti", "Haití");
        addTranslation(translations, "Honduras", "Honduras");
        addTranslation(translations, "Hong Kong", "Hong Kong");
        addTranslation(translations, "Hungary", "Hungría");
        addTranslation(translations, "India", "India");
        addTranslation(translations, "Indonesia", "Indonesia");
        addTranslation(translations, "Iran", "Irán");
        addTranslation(translations, "Iraq", "Irak");
        addTranslation(translations, "Ireland", "Irlanda");
        addTranslation(translations, "Iceland", "Islandia");
        addTranslation(translations, "Italy", "Italia");
        addTranslation(translations, "Jamaica", "Jamaica");
        addTranslation(translations, "Japan", "Japón");
        addTranslation(translations, "Jordan", "Jordania");
        addTranslation(translations, "Kazakhstan", "Kazajistán");
        addTranslation(translations, "Kenya", "Kenia");
        addTranslation(translations, "Kyrgyzstan", "Kirguistán");
        addTranslation(translations, "Kuwait", "Kuwait");
        addTranslation(translations, "Lebanon", "Líbano");
        addTranslation(translations, "Laos", "Laos");
        addTranslation(translations, "Latvia", "Letonia");
        addTranslation(translations, "Liberia", "Liberia");
        addTranslation(translations, "Libya", "Libia");
        addTranslation(translations, "Liechtenstein", "Liechtenstein");
        addTranslation(translations, "Lithuania", "Lituania");
        addTranslation(translations, "Luxembourg", "Luxemburgo");
        addTranslation(translations, "Mexico", "México");
        addTranslation(translations, "Monaco", "Mónaco");
        addTranslation(translations, "Macao", "Macao");
        addTranslation(translations, "Macedonia", "Macedonia");
        addTranslation(translations, "Madagascar", "Madagascar");
        addTranslation(translations, "Malaysia", "Malasia");
        addTranslation(translations, "Malawi", "Malawi");
        addTranslation(translations, "Mali", "Mali");
        addTranslation(translations, "Malta", "Malta");
        addTranslation(translations, "Morocco", "Marruecos");
        addTranslation(translations, "Mauritius", "Mauricio");
        addTranslation(translations, "Mauritania", "Mauritania");
        addTranslation(translations, "Moldova", "Moldavia");
        addTranslation(translations, "Mongolia", "Mongolia");
        addTranslation(translations, "Montenegro", "Montenegro");
        addTranslation(translations, "Mozambique", "Mozambique");
        addTranslation(translations, "Namibia", "Namibia");
        addTranslation(translations, "Nepal", "Nepal");
        addTranslation(translations, "Netherlands", "Países Bajos");
        addTranslation(translations, "New Zealand", "Nueva Zelanda");
        addTranslation(translations, "Nicaragua", "Nicaragua");
        addTranslation(translations, "Niger", "Níger");
        addTranslation(translations, "Nigeria", "Nigeria");
        addTranslation(translations, "Norway", "Noruega");
        addTranslation(translations, "Oman", "Omán");
        addTranslation(translations, "Pakistan", "Pakistán");
        addTranslation(translations, "Panama", "Panamá");
        addTranslation(translations, "Papua New Guinea", "Papúa Nueva Guinea");
        addTranslation(translations, "Paraguay", "Paraguay");
        addTranslation(translations, "Peru", "Perú");
        addTranslation(translations, "Poland", "Polonia");
        addTranslation(translations, "Portugal", "Portugal");
        addTranslation(translations, "Qatar", "Catar");
        addTranslation(translations, "Romania", "Rumanía");
        addTranslation(translations, "Russia", "Rusia");
        addTranslation(translations, "Senegal", "Senegal");
        addTranslation(translations, "Serbia", "Serbia");
        addTranslation(translations, "Singapore", "Singapur");
        addTranslation(translations, "South Africa", "Sudáfrica");
        addTranslation(translations, "Sudan", "Sudán");
        addTranslation(translations, "Sweden", "Suecia");
        addTranslation(translations, "Switzerland", "Suiza");
        addTranslation(translations, "Syria", "Siria");
        addTranslation(translations, "Thailand", "Tailandia");
        addTranslation(translations, "Tunisia", "Túnez");
        addTranslation(translations, "Turkey", "Turquía");
        addTranslation(translations, "Ukraine", "Ucrania");
        addTranslation(translations, "United Kingdom", "Reino Unido");
        addTranslation(translations, "Uruguay", "Uruguay");
        addTranslation(translations, "Uzbekistan", "Uzbekistán");
        addTranslation(translations, "Venezuela", "Venezuela");
        addTranslation(translations, "Vietnam", "Vietnam");
        addTranslation(translations, "Yemen", "Yemen");
        addTranslation(translations, "Zambia", "Zambia");
        addTranslation(translations, "Zimbabwe", "Zimbabue");
        addTranslation(translations, "Curaçao", "Curazao");
        //metidos a mano
        addTranslation(translations, "Northern Ireland", "Irlanda del Norte");
        addTranslation(translations, "Wales", "Gales");
        addTranslation(translations, "Kosovo", "Kosovo");
        addTranslation(translations, "North Macedonia", "Macedonia del Norte");
        addTranslation(translations, "Czech Republic", "Republica Checa");
        addTranslation(translations, "Republic of Ireland", "República de Irlanda");
        addTranslation(translations, "Scotland", "Escocia");
        addTranslation(translations, "England", "Inglaterra");
        addTranslation(translations, "Czechia", "Republica Checa");
        addTranslation(translations, "Congo DR", "RD Congo");
        addTranslation(translations, "Iraq", "Irak");

        return translations;
    }

    /**
     * Guardo la clave original y una versión normalizada para hacer la búsqueda más robusta.
     */
    private static void addTranslation(Map<String, String> translations, String englishName, String spanishName) {
        translations.put(englishName, spanishName);

        String normalizedKey = Normalizer.normalize(englishName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase();

        translations.put(normalizedKey, spanishName);
    }
}