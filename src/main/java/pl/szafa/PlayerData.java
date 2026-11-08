package pl.szafa;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dane jednego gracza: posiadane rzeczy (id -> czas wygasniecia w ms) i aktywne. */
public final class PlayerData {
    public final Map<String, Long> pets = new LinkedHashMap<>();
    public final Map<String, Long> costumes = new LinkedHashMap<>();
    public String activePet;
    public String activeCostume;
}
