package ca.IR;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Paths;

public class IndexFiles {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: IndexFiles <indexDir> <docsDir>");
            return;
        }

        String indexDir = args[0];
        String docsDir = args[1];

        File docs = new File(docsDir);
        if (!docs.exists() || !docs.isDirectory()) {
            System.out.println("Document directory does not exist or is not a directory: " + docsDir);
            return;
        }

        Directory dir = FSDirectory.open(Paths.get(indexDir));

        EnglishAnalyzer analyzer = new EnglishAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        IndexWriter writer = new IndexWriter(dir, config);

        File[] files = docs.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    System.out.println("Indexing file: " + file.getName());
                    indexDoc(writer, file);
                }
            }
        } else {
            System.out.println("No files found in the directory: " + docsDir);
        }

        writer.close();
        System.out.println("Indexing completed.");
    }

    static void indexDoc(IndexWriter writer, File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            String docID = null;
            StringBuilder title = new StringBuilder();
            StringBuilder author = new StringBuilder();
            StringBuilder content = new StringBuilder();
            boolean isContent = false;

            while ((line = br.readLine()) != null) {
                if (line.startsWith(".I")) {
                    if (docID != null) {
                        addDocument(writer, docID, title.toString(), author.toString(), content.toString());
                        title.setLength(0);
                        author.setLength(0);
                        content.setLength(0);
                    }
                    docID = line.substring(3).trim();
                } else if (line.startsWith(".T")) {
                    line = br.readLine();
                    while (line != null && !line.startsWith(".")) {
                        title.append(line).append(" ");
                        line = br.readLine();
                    }
                } else if (line.startsWith(".A")) {
                    line = br.readLine();
                    while (line != null && !line.startsWith(".")) {
                        author.append(line).append(" ");
                        line = br.readLine();
                    }
                } else if (line.startsWith(".W")) {
                    isContent = true;
                    continue;
                }

                if (isContent) {
                    content.append(line).append(" ");
                }
            }

            if (docID != null) {
                addDocument(writer, docID, title.toString(), author.toString(), content.toString());
            }
        }
    }

    private static void addDocument(IndexWriter writer, String docID, String title, String author, String textContent)
            throws IOException {
        Document doc = new Document();
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("author", author, Field.Store.YES));
        doc.add(new TextField("contents", textContent, Field.Store.YES));
        doc.add(new StringField("documentID", docID, Field.Store.YES));

        writer.addDocument(doc);
    }
}
