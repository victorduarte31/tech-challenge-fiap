package br.com.oficina.application;

/**
 * Normalização de parâmetros de paginação das listagens.
 * Protege contra índices negativos e tamanhos de página abusivos.
 */
public final class Pagination {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Pagination() {
    }

    /** Índice de página normalizado (nunca negativo). */
    public static int page(int page) {
        return Math.max(page, 0);
    }

    /** Tamanho de página normalizado para o intervalo [1, {@value #MAX_SIZE}]. */
    public static int cap(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
