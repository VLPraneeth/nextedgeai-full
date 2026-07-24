package com.syncari.core.service.mapper;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.KStemFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.TrimFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.util.CharsRef;

import java.io.IOException;

public class AttributeAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        final StandardTokenizer src = new StandardTokenizer();
        TokenStream result = new LowerCaseFilter(src);
        result = new ASCIIFoldingFilter(result);
        result = new StopFilter(result, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);
        result = new KStemFilter(result);
        result = new TrimFilter(result);
        final SynonymMap.Builder builder = new SynonymMap.Builder(true);
        //This can be externalized at some point, and with a UI,
        //can be customized by CX
        addSynonym(builder, "company", "account");
        addSynonym(builder, "lead", "person");
        addSynonym(builder, "lead", "people");
        addSynonym(builder, "lead", "contact");
        addSynonym(builder, "ltd", "limited");
        addSynonym(builder, "st", "street");
        addSynonym(builder, "ave", "avenue");
        addSynonym(builder, "postal", "zip");
        addSynonym(builder, "blvd", "boulevard");
        addSynonym(builder, "pvt", "private");
        addSynonym(builder, "inc", "incorporated");
        addSynonym(builder, "cell", "mobile");
        try {
            result = new SynonymGraphFilter(result, builder.build(), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new TokenStreamComponents(src, result);
    }

    private static void addSynonym(SynonymMap.Builder builder, String value, String syn) {
        builder.add(new CharsRef(value), new CharsRef(syn), true);
        builder.add(new CharsRef(syn), new CharsRef(value), true);
    }

}