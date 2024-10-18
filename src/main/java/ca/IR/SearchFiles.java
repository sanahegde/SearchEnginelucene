package ca.IR;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
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

    // Define the custom analyzer directly inside SearchFiles.java
    public static class CustomAnalyzer extends Analyzer {
        private static final List<String> STOP_WORDS = Arrays.asList(
                "a", "an", "the", "and", "or", "is", "are", "was", "were", "this", "that", "it", "on", "in", "at", "by"
        // Add more custom stop words if needed
        );
        private static final CharArraySet STOP_WORD_SET = new CharArraySet(STOP_WORDS, true);

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            // Tokenize the text
            StandardTokenizer tokenizer = new StandardTokenizer();
            TokenStream tokenStream = new LowerCaseFilter(tokenizer); // Convert to lowercase
            tokenStream = new StopFilter(tokenStream, STOP_WORD_SET); // Apply custom stop words
            tokenStream = new PorterStemFilter(tokenStream); // Apply Porter Stemming

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
            setBM25Similarity(searcher); // Aggressively tuned BM25

            // Using the embedded CustomAnalyzer instead of EnglishAnalyzer
            CustomAnalyzer analyzer = new CustomAnalyzer();
            String[] fields = { "title", "author", "contents" };

            // Aggressive field boosts
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("title", 5.0f); // Further boost title
            boosts.put("author", 4.0f); // Further boost author
            boosts.put("contents", 1.0f); // Normal weight for contents

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

                        ScoreDoc[] hits = searcher.search(query, 100).scoreDocs; // Retrieve top 100 results

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

    private static void setBM25Similarity(IndexSearcher searcher) {
        // More aggressive BM25 tuning
        BM25Similarity bm25 = new BM25Similarity(2.5f, 0.3f); // Optimized parameters for better ranking
        searcher.setSimilarity(bm25);
    }
}
