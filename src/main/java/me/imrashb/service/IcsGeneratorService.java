package me.imrashb.service;

import me.imrashb.domain.*;
import me.imrashb.domain.combinaison.CombinaisonHoraire;

import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Service
public class IcsGeneratorService {

    private static final DateTimeFormatter ICS_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final String PRODID = "-//HoraireETS//Course Schedule//EN";
    
    /**
     * Generate ICS calendar file from a schedule combination
     * @param combinaison The schedule combination
     * @param startDate Optional start date (if null, calculated from session)
     * @param endDate Optional end date (if null, calculated from session)
     * @return ICS file content as string
     */
    public String generateIcsFromCombinaison(CombinaisonHoraire combinaison, LocalDate startDate, LocalDate endDate) {
        if (combinaison == null || combinaison.getGroupes() == null || combinaison.getGroupes().isEmpty()) {
            throw new IllegalArgumentException("Combinaison must not be null and must contain at least one groupe");
        }

        // Get session from first groupe
        Session session = combinaison.getGroupes().get(0).getCours().getSession();
        
        // Calculate dates if not provided
        if (startDate == null || endDate == null) {
            LocalDate[] semesterDates = calculateSemesterDates(session);
            if (startDate == null) {
                startDate = semesterDates[0];
            }
            if (endDate == null) {
                endDate = semesterDates[1];
            }
        }

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:").append(PRODID).append("\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");

        // Generate events for each groupe and its activities
        for (Groupe groupe : combinaison.getGroupes()) {
            Cours cours = groupe.getCours();
            String sigle = cours.getSigle();
            String titre = cours.getTitre() != null ? cours.getTitre() : "";

            for (Activite activite : groupe.getActivites()) {
                if (activite.getHoraire() != null && activite.getHoraire().getJour() != null) {
                    String vevent = createVEvent(activite, sigle, titre, startDate, endDate, groupe.getNumeroGroupe());
                    ics.append(vevent);
                }
            }
        }

        ics.append("END:VCALENDAR\r\n");
        return ics.toString();
    }

    /**
     * Generate ICS calendar file from a single course
     * @param cours The course
     * @param startDate Optional start date (if null, calculated from session)
     * @param endDate Optional end date (if null, calculated from session)
     * @return ICS file content as string
     */
    public String generateIcsFromCours(Cours cours, LocalDate startDate, LocalDate endDate) {
        if (cours == null || cours.getGroupes() == null || cours.getGroupes().isEmpty()) {
            throw new IllegalArgumentException("Cours must not be null and must contain at least one groupe");
        }

        Session session = cours.getSession();
        
        // Calculate dates if not provided
        if (startDate == null || endDate == null) {
            LocalDate[] semesterDates = calculateSemesterDates(session);
            if (startDate == null) {
                startDate = semesterDates[0];
            }
            if (endDate == null) {
                endDate = semesterDates[1];
            }
        }

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:").append(PRODID).append("\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");

        String sigle = cours.getSigle();
        String titre = cours.getTitre() != null ? cours.getTitre() : "";

        // Generate events for each groupe (typically only one groupe per course in this context)
        for (Groupe groupe : cours.getGroupes()) {
            for (Activite activite : groupe.getActivites()) {
                if (activite.getHoraire() != null && activite.getHoraire().getJour() != null) {
                    String vevent = createVEvent(activite, sigle, titre, startDate, endDate, groupe.getNumeroGroupe());
                    ics.append(vevent);
                }
            }
        }

        ics.append("END:VCALENDAR\r\n");
        return ics.toString();
    }

    /**
     * Calculate semester start and end dates based on session
     * @param session The session (year + trimester)
     * @return Array with [startDate, endDate]
     */
    public LocalDate[] calculateSemesterDates(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }

        int year = session.getAnnee();
        Trimestre trimestre = session.getTrimestre();
        LocalDate startDate;
        LocalDate endDate;

        switch (trimestre) {
            case HIVER:
                // First Monday of January
                startDate = LocalDate.of(year, 1, 1).with(java.time.temporal.TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
                // Last Friday of April
                endDate = LocalDate.of(year, 4, 1).with(java.time.temporal.TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY));
                break;
            case ETE:
                // First Monday of May
                startDate = LocalDate.of(year, 5, 1).with(java.time.temporal.TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
                // Last Friday of August
                endDate = LocalDate.of(year, 8, 1).with(java.time.temporal.TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY));
                break;
            case AUTOMNE:
                // First Monday of September
                startDate = LocalDate.of(year, 9, 1).with(java.time.temporal.TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
                // Last Friday of December
                endDate = LocalDate.of(year, 12, 1).with(java.time.temporal.TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY));
                break;
            default:
                throw new IllegalArgumentException("Unknown trimester: " + trimestre);
        }

        return new LocalDate[]{startDate, endDate};
    }

    /**
     * Create a VEVENT entry for an activity
     * @param activite The activity
     * @param sigle Course code
     * @param titre Course title
     * @param semesterStart Semester start date
     * @param semesterEnd Semester end date
     * @param numeroGroupe Group number
     * @return VEVENT string
     */
    private String createVEvent(Activite activite, String sigle, String titre, 
                                LocalDate semesterStart, LocalDate semesterEnd, String numeroGroupe) {
        HoraireActivite horaire = activite.getHoraire();
        Jour jour = horaire.getJour();
        
        // Find first occurrence of the day of week in semester
        LocalDate firstDate = semesterStart.with(java.time.temporal.TemporalAdjusters.nextOrSame(
            dayOfWeekFromJour(jour)));

        // Calculate start and end times
        int heureDepart = horaire.getHeureDepart();
        int heureFin = horaire.getHeureFin();
        
        int startHour = heureDepart / 100;
        int startMinute = (heureDepart % 100) * 60 / 100;
        int endHour = heureFin / 100;
        int endMinute = (heureFin % 100) * 60 / 100;

        LocalDateTime dtStart = firstDate.atTime(startHour, startMinute);
        LocalDateTime dtEnd = firstDate.atTime(endHour, endMinute);

        // Format dates for ICS (use UTC timezone)
        String dtStartStr = formatDateTime(dtStart);
        String dtEndStr = formatDateTime(dtEnd);
        String untilStr = formatDateTime(semesterEnd.atTime(23, 59, 59));

        // Create unique ID
        String uid = sigle + "-" + numeroGroupe + "-" + activite.getNom() + "-" + 
                     dtStartStr + "@horaire-ets";

        // Build summary
        String summary = sigle;
        if (numeroGroupe != null && !numeroGroupe.isEmpty()) {
            summary += "-" + numeroGroupe;
        }
        summary += " - " + activite.getNom();
        if (titre != null && !titre.isEmpty()) {
            // Extract title without course code prefix if present
            String titleOnly = titre.contains(" - ") ? titre.substring(titre.indexOf(" - ") + 3) : titre;
            summary += ": " + titleOnly;
        }

        // Build description
        StringBuilder description = new StringBuilder();
        description.append("Cours: ").append(sigle);
        if (titre != null && !titre.isEmpty()) {
            description.append("\\n").append("Titre: ").append(titre);
        }
        if (activite.getNom() != null && !activite.getNom().isEmpty()) {
            description.append("\\n").append("Activité: ").append(activite.getNom());
        }
        if (activite.getModeEnseignement() != null) {
            description.append("\\n").append("Mode: ").append(activite.getModeEnseignement());
        }
        if (!activite.getCharges().isEmpty()) {
            description.append("\\n").append("Enseignant(s): ").append(String.join(", ", activite.getCharges()));
        }

        // Build location
        String location = String.join(", ", activite.getLocaux());
        if (location.isEmpty()) {
            location = "Non spécifié";
        }

        // Build RRULE
        String rrule = "FREQ=WEEKLY;BYDAY=" + dayToIcsDay(jour) + ";UNTIL=" + untilStr + "Z";

        // Current timestamp for DTSTAMP
        String dtStamp = formatDateTime(LocalDateTime.now());

        // Build VEVENT
        StringBuilder vevent = new StringBuilder();
        vevent.append("BEGIN:VEVENT\r\n");
        vevent.append("UID:").append(escapeIcsText(uid)).append("\r\n");
        vevent.append("DTSTART:").append(dtStartStr).append("Z\r\n");
        vevent.append("DTEND:").append(dtEndStr).append("Z\r\n");
        vevent.append("RRULE:").append(rrule).append("\r\n");
        vevent.append("SUMMARY:").append(escapeIcsText(summary)).append("\r\n");
        vevent.append("DESCRIPTION:").append(escapeIcsText(description.toString())).append("\r\n");
        vevent.append("LOCATION:").append(escapeIcsText(location)).append("\r\n");
        vevent.append("DTSTAMP:").append(dtStamp).append("Z\r\n");
        vevent.append("END:VEVENT\r\n");

        return vevent.toString();
    }

    /**
     * Format LocalDateTime to ICS format (YYYYMMDDTHHmmss)
     * @param dateTime The date-time to format
     * @return Formatted string
     */
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(ICS_DATETIME_FORMAT);
    }

    /**
     * Convert Jour enum to DayOfWeek
     * @param jour The Jour enum value
     * @return DayOfWeek
     */
    private DayOfWeek dayOfWeekFromJour(Jour jour) {
        switch (jour) {
            case LUNDI:
                return DayOfWeek.MONDAY;
            case MARDI:
                return DayOfWeek.TUESDAY;
            case MERCREDI:
                return DayOfWeek.WEDNESDAY;
            case JEUDI:
                return DayOfWeek.THURSDAY;
            case VENDREDI:
                return DayOfWeek.FRIDAY;
            case SAMEDI:
                return DayOfWeek.SATURDAY;
            case DIMANCHE:
                return DayOfWeek.SUNDAY;
            default:
                throw new IllegalArgumentException("Unknown jour: " + jour);
        }
    }

    /**
     * Convert Jour enum to ICS day format (MO, TU, WE, etc.)
     * @param jour The Jour enum value
     * @return ICS day string
     */
    private String dayToIcsDay(Jour jour) {
        switch (jour) {
            case LUNDI:
                return "MO";
            case MARDI:
                return "TU";
            case MERCREDI:
                return "WE";
            case JEUDI:
                return "TH";
            case VENDREDI:
                return "FR";
            case SAMEDI:
                return "SA";
            case DIMANCHE:
                return "SU";
            default:
                throw new IllegalArgumentException("Unknown jour: " + jour);
        }
    }

    /**
     * Escape special characters in ICS text fields
     * @param text The text to escape
     * @return Escaped text
     */
    private String escapeIcsText(String text) {
        if (text == null) {
            return "";
        }
        // Escape backslash, comma, semicolon, newline
        return text.replace("\\", "\\\\")
                   .replace(",", "\\,")
                   .replace(";", "\\;")
                   .replace("\n", "\\n")
                   .replace("\r", "");
    }
}
