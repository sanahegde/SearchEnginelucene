package ca.IR;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class SearchFiles {

    public static class CustomAnalyzer extends Analyzer {
        private static final List<String> STOP_WORDS = Arrays.asList(
                "a", "an", "the", "and", "or", "is", "are", "was", "were", "this", "that", "it", "on", "in", "at",
                "by");
        private static final CharArraySet STOP_WORD_SET = new CharArraySet(STOP_WORDS, true);

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            StandardTokenizer tokenizer = new StandardTokenizer();
            TokenStream tokenStream = new LowerCaseFilter(tokenizer);
            tokenStream = new StopFilter(tokenStream, STOP_WORD_SET);
            tokenStream = new PorterStemFilter(tokenStream);
            return new TokenStreamComponents(tokenizer, tokenStream);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: SearchFiles <indexDir> <queriesFile> <scoreType> <outputFile>");
            return;
        }

        String indexDir = args[0];
        String queriesFile = args[1];
        int scoreType = Integer.parseInt(args[2]);
        String outputFile = args[3];

        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
                PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {

            IndexSearcher searcher = new IndexSearcher(reader);
            setBM25Similarity(searcher);

            // Using the custom analyzer
            CustomAnalyzer analyzer = new CustomAnalyzer();
            String[] fields = { "title", "author", "contents" };

            // Field boosts (reduced aggressive boosts for better performance)
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("title", 3.0f); // Lowering boost on title
            boosts.put("author", 2.0f); // Lowering boost on author
            boosts.put("contents", 1.0f); // Keeping contents boost neutral

            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boosts);

            File queryFile = new File(queriesFile);
            try (Scanner scanner = new Scanner(queryFile)) {
                int queryNum = 1;

                while (scanner.hasNextLine()) {
                    String queryString = scanner.nextLine().trim();
                    if (queryString.isEmpty())
                        continue;

                    try {
                        queryString = QueryParser.escape(queryString);
                        Query query = parser.parse(queryString);

                        ScoreDoc[] hits = searcher.search(query, 1400).scoreDocs;

                        int rank = 1;
                        for (ScoreDoc hit : hits) {
                            Document doc = searcher.doc(hit.doc);
                            String docID = doc.get("documentID");
                            writer.println(queryNum + " 0 " + docID + " " + rank + " " + hit.score + " STANDARD");
                            rank++;
                        }
                        queryNum++;
                    } catch (Exception e) {
                        System.out.println("Error parsing query: " + queryString);
                    }
                }
            }
        }
    }

    // Tuning BM25
    private static void setBM25Similarity(IndexSearcher searcher) {
        BM25Similarity bm25 = new BM25Similarity(1.2f, 0.75f); // Less aggressive parameters
        searcher.setSimilarity(bm25);
    }
}
