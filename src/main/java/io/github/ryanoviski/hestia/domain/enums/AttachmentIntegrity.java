package io.github.ryanoviski.hestia.domain.enums;

public enum AttachmentIntegrity {
    AVAILABLE("Disponível"), MISSING("Arquivo ausente"), MODIFIED("Arquivo alterado"), INVALID("Arquivo inválido");
    private final String displayName;
    AttachmentIntegrity(String displayName){this.displayName=displayName;}
    public String displayName(){return displayName;}
}
