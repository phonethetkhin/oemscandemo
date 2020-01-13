package com.example.oemscandemo;

public interface ScanListener {

    public void result(String content);

    public void henResult(String codeType, String context);

    public void DisplayDecodeResults();

    public void DisplayMultireadResults();

}
