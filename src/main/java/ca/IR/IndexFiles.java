package ca.IR;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;

public class IndexFiles {

    // Reuse the same analyzer throughout the program
    private static final StandardAnalyzer analyzer = new StandardAnalyzer();

    public static void main(String[] args) throws Exception {
        // Ensure required arguments are provided
        if (args.length < 2) {
            System.out.println("Usage: java IndexFiles <indexPath> <docsPath>");
            return;
        }

        String indexPath = args[0]; // Index location
        String docsPath = args[1]; // Document directory

        // Check if the documents directory exists
        File docDir = new File(docsPath);
        if (!docDir.exists() || !docDir.isDirectory()) {
            System.out.println("Document directory '" + docsPath + "' does not exist or is not a directory");
            return;
        }

        // Initialize the Lucene index directory
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        // Start indexing documents
        try (IndexWriter writer = new IndexWriter(dir, config)) {
            File[] files = docDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        System.out.println("Indexing: " + file.getName());
                        processDocument(writer, file);
                    }
                }
            } else {
                System.out.println("No files found in " + docsPath);
            }
        }

        System.out.println("Indexing completed.");
    }

    // Method to process and index a single document
    private static void processDocument(IndexWriter writer, File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            StringBuilder contentBuilder = new StringBuilder();
            int docID = 0;
            String title = "";

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(".I")) {
                    // Index the previous document if content exists
                    if (contentBuilder.length() > 0) {
                        addToIndex(writer, String.valueOf(docID), title, contentBuilder.toString());
                        contentBuilder.setLength(0); // Clear the content for the next document
                        title = ""; // Reset title
                    }

                    docID = Integer.parseInt(line.split(" ")[1].trim());
                    System.out.println("Document ID: " + docID);
                } else if (line.startsWith(".T")) {
                    StringBuilder titleBuilder = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(".A") || line.startsWith(".B")) {
                            break;
                        }
                        titleBuilder.append(line.trim()).append(" ");
                    }
                    title = titleBuilder.toString().trim();
                    System.out.println("Title: " + title);
                } else if (line.startsWith(".W")) {
                    StringBuilder content = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(".I")) {
                            contentBuilder.append(content.toString().trim());
                            addToIndex(writer, String.valueOf(docID), title, contentBuilder.toString());
                            contentBuilder.setLength(0);
                            docID = Integer.parseInt(line.split(" ")[1].trim());
                            System.out.println("Next Document ID: " + docID);
                            break;
                        }
                        content.append(line.trim()).append(" ");
                    }

                    if (line == null) {
                        contentBuilder.append(content.toString().trim());
                        addToIndex(writer, String.valueOf(docID), title, contentBuilder.toString());
                    }
                }
            }
        }
    }

    // Method to add a document to the index
    private static void addToIndex(IndexWriter writer, String docID, String title, String content) throws IOException {
        Document doc = new Document();

        // Add fields to the Lucene document
        doc.add(new StringField("docID", docID, Field.Store.YES));
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("content", content, Field.Store.YES));

        writer.addDocument(doc);
    }
}
