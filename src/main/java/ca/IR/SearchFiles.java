package ca.IR;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class SearchFiles {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: SearchFiles <indexDir> <queriesFile> <scoreType> <outputFile>");
            return;
        }

        String indexPath = args[0];
        String queriesPath = args[1];
        int scoreType = Integer.parseInt(args[2]);
        String outputPath = args[3];

        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
             PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {

            IndexSearcher searcher = new IndexSearcher(reader);
            setSimilarity(searcher, scoreType);

            StandardAnalyzer analyzer = new StandardAnalyzer();
            String[] fields = {"title", "author", "contents"};
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("title", 2.0f);  // Boost title at query time
            boosts.put("author", 1.5f); // Boost author at query time
            boosts.put("contents", 1.0f);

            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boosts);

            File queryFile = new File(queriesPath);
            try (Scanner scanner = new Scanner(queryFile)) {
                int queryNumber = 1;

                while (scanner.hasNextLine()) {
                    String queryString = scanner.nextLine().trim();
                    if (queryString.isEmpty()) continue;

                    try {
                        queryString = QueryParser.escape(queryString);
                        Query query = parser.parse(queryString);

                        // Run the search
                        ScoreDoc[] hits = searcher.search(query, 100).scoreDocs;  // Retrieve top 100 results

                        int rank = 1;
                        for (ScoreDoc hit : hits) {
                            Document doc = searcher.doc(hit.doc);
                            String docID = doc.get("documentID");
                            writer.println(queryNumber + " 0 " + docID + " " + rank + " " + hit.score + " STANDARD");
                            rank++;
                        }
                        queryNumber++;
                    } catch (Exception e) {
                        System.out.println("Error parsing query: " + queryString);
                    }
                }
            }
        }
    }

    private static void setSimilarity(IndexSearcher searcher, int scoreType) {
        switch (scoreType) {
            case 0:
                searcher.setSimilarity(new ClassicSimilarity());
                break;
            case 1:
                searcher.setSimilarity(new BM25Similarity(1.5f, 0.75f)); // Tuned BM25
                break;
            case 2:
                searcher.setSimilarity(new BooleanSimilarity());
                break;
            case 3:
                searcher.setSimilarity(new LMDirichletSimilarity());
                break;
            case 4:
                searcher.setSimilarity(new LMJelinekMercerSimilarity(0.7f));
                break;
            default:
                System.out.println("Invalid score type");
                throw new IllegalArgumentException("Invalid score type");
        }
    }
}
