package io.github.ryanoviski.hestia.domain.enums;

public enum DocumentType {
    RECEIPT("Comprovante"), PAYSLIP("Holerite"), INVOICE("Conta ou documento de cobrança"), OTHER("Outro");
    private final String displayName;
    DocumentType(String displayName){this.displayName=displayName;}
    public String displayName(){return displayName;}
}
