package mz.contas.domain;

public enum MovementType {
    SALDO_INICIAL(Nature.CREDITO),
    TRANSFERENCIA_DEBITO(Nature.DEBITO),
    TRANSFERENCIA_CREDITO(Nature.CREDITO);

    public enum Nature { DEBITO, CREDITO }

    private final Nature nature;

    MovementType(Nature nature) { this.nature = nature; }

    public Nature getNature() { return nature; }
}

