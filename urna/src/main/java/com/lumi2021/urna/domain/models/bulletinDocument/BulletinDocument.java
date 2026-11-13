package com.lumi2021.urna.domain.models.bulletinDocument;

import java.util.List;

public class BulletinDocument {
    private List<BulletinElement> elements;

    public BulletinDocument(List<BulletinElement> elements) {
        this.elements = elements;
    }

    public List<BulletinElement> elements() {
        return elements;
    }

    /** Versão em texto puro (uma linha por elemento), para console e arquivo. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (BulletinElement element : elements) {
            if (element instanceof BulletinLine line) {
                sb.append(line.content().stripTrailing()).append('\n');
            }
        }
        return sb.toString();
    }
}
