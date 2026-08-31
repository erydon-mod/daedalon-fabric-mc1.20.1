package com.oliver.daedalon.registry;

/**
 * The stone finishes shared by Daedalon's freestanding decor.
 *
 * <p>The serialized names deliberately match the existing asset and registry
 * paths so migrating a family does not also rename its material variants.</p>
 */
public enum DecorMaterial {
    AGANITE("aganite"),
    ATERZON("aterzon"),
    BOREALIS("borealis"),
    BRECTITE("brectite"),
    CALACATTUM("calacattum"),
    CHALSTROM("chalstrom"),
    CHRYSONYX("chrysonyx"),
    ETRUSCUS("etruscus"),
    GELASTRUM("gelastrum"),
    GLACIUM("glacium"),
    HESPERION("hesperion"),
    IMPERIUM("imperium"),
    KYLORION("kylorion"),
    KELASTRION("kelastrion"),
    LATMION("latmion"),
    LAURENTIUM("laurentium"),
    MIELONYX("mielonyx"),
    NERIUM("nerium"),
    NOXOPLIS("noxoplis"),
    PORPHYROS("porphyros"),
    PSAMATHEON("psamatheon"),
    PORTORIUM("portorium"),
    ROSINIUM("rosinium"),
    SANGUENITE("sanguenite"),
    SELENEPHOS("selenephos"),
    SOLISTRA("solistra"),
    STRIATUS("striatus");

    private final String id;

    DecorMaterial(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
