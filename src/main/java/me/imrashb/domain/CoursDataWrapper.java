package me.imrashb.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;

@Data
@NoArgsConstructor
@Table(name = "cours_data")
@Entity
public class CoursDataWrapper {
    @Id
    @Column(name = "sigle", nullable = false)
    private String sigle;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> prealables;

    @Column(name = "credits", nullable = false)
    private Integer credits;

    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "responsable")
    private String responsable;

    @Column(name = "cycle")
    private String cycle;

    @Column(name = "charge_travail_cours")
    private Integer chargeTravailCours;

    @Column(name = "charge_travail_laboratoire")
    private Integer chargeTravailLaboratoire;

    @Column(name = "disponible")
    private Boolean disponible;

    /**
     * Returns the course code prefix from the title (e.g., "MAT380" from "MAT380 - Algèbre linéaire")
     * @return Course code prefix or null if title doesn't contain " - "
     */
    public String getTitlePrefix() {
        if (titre != null && titre.contains(" - ")) {
            return titre.substring(0, titre.indexOf(" - ")).trim();
        }
        return null;
    }

    /**
     * Returns the title without the course code prefix (e.g., "Algèbre linéaire" from "MAT380 - Algèbre linéaire")
     * @return Title postfix or full title if no prefix is present
     */
    public String getTitlePostfix() {
        if (titre != null && titre.contains(" - ")) {
            return titre.substring(titre.indexOf(" - ") + 3).trim();
        }
        return titre;
    }

    /**
     * Checks if the title contains a course code prefix
     * @return true if title contains " - ", false otherwise
     */
    public boolean hasTitlePrefix() {
        return titre != null && titre.contains(" - ");
    }

}
