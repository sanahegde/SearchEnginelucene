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

        String indexLocation = args[0];
        String queryFileLocation = args[1];
        int similarityType = Integer.parseInt(args[2]);
        String outputFileLocation = args[3];

        try (DirectoryReader dirReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexLocation)));
                PrintWriter resultWriter = new PrintWriter(new FileWriter(outputFileLocation))) {

            IndexSearcher indexSearcher = new IndexSearcher(dirReader);
            setSearcherSimilarity(indexSearcher, similarityType);

            StandardAnalyzer analyzer = new StandardAnalyzer();
            String[] queryFields = { "title", "author", "contents" };
            Map<String, Float> fieldBoosts = new HashMap<>();
            fieldBoosts.put("title", 2.0f);
            fieldBoosts.put("author", 1.5f);
            fieldBoosts.put("contents", 1.0f);

            MultiFieldQueryParser queryParser = new MultiFieldQueryParser(queryFields, analyzer, fieldBoosts);

            File queryFile = new File(queryFileLocation);
            try (Scanner fileScanner = new Scanner(queryFile)) {
                int queryCounter = 1;

                while (fileScanner.hasNextLine()) {
                    String queryInput = fileScanner.nextLine().trim();
                    if (queryInput.isEmpty())
                        continue;

                    try {
                        queryInput = QueryParser.escape(queryInput);
                        Query userQuery = queryParser.parse(queryInput);

                        ScoreDoc[] topHits = indexSearcher.search(userQuery, 100).scoreDocs;
                        int resultRank = 1;

                        for (ScoreDoc scoreDoc : topHits) {
                            Document document = indexSearcher.doc(scoreDoc.doc);
                            String documentID = document.get("documentID");
                            resultWriter.println(queryCounter + " 0 " + documentID + " " + resultRank + " "
                                    + scoreDoc.score + " STANDARD");
                            resultRank++;
                        }
                        queryCounter++;
                    } catch (Exception e) {
                        System.out.println("Error in query parsing: " + queryInput);
                    }
                }
            }
        }
    }

    private static void setSearcherSimilarity(IndexSearcher searcher, int similarityType) {
        switch (similarityType) {
            case 0:
                searcher.setSimilarity(new ClassicSimilarity());
                break;
            case 1:
                searcher.setSimilarity(new BM25Similarity(1.5f, 0.75f));
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
                System.out.println("Invalid similarity type.");
                throw new IllegalArgumentException("Unsupported similarity type:.");
        }
    }
}
