package io.github.astromg01.nucleo.export;

interface IShizukuExportService {
    void destroy() = 16777114;
    String beginExport(String requestedName) = 1;
    void writeChunk(in byte[] chunk) = 2;
    String finishExport() = 3;
    void abortExport() = 4;
}
