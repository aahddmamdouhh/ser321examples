/*
Simple Web Server in Java which allows you to call 
localhost:9000/ and show you the root.html webpage from the www/root.html folder
You can also do some other simple GET requests:
1) /random shows you a random picture (well random from the set defined)
2) json shows you the response as JSON for /random instead the html page
3) /file/filename shows you the raw file (not as HTML)
4) /multiply?num1=3&num2=4 multiplies the two inputs and responses with the result
5) /github?query=users/amehlhase316/repos (or other GitHub repo owners) will lead to receiving
   JSON which will for now only be printed in the console. See the todo below

The reading of the request is done "manually", meaning no library that helps making things a 
little easier is used. This is done so you see exactly how to pars the request and 
write a response back
*/

package funHttpServer;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.charset.Charset;

class WebServer {
  public static void main(String args[]) {
    WebServer server = new WebServer(9000);
  }

  /**
   * Main thread
   * @param port to listen on
   */
  public WebServer(int port) {
    ServerSocket server = null;
    Socket sock = null;
    InputStream in = null;
    OutputStream out = null;

    try {
      server = new ServerSocket(port);
      System.out.println("Server started on port " + port);
      
      while (true) {
        try {
          sock = server.accept();
          out = sock.getOutputStream();
          in = sock.getInputStream();
          byte[] response = createResponse(in);
          out.write(response);
          out.flush();
        } catch (SocketException e) {
          System.out.println("Socket error: " + e.getMessage());
          // Continue listening for new connections
        } catch (IOException e) {
          System.out.println("IO error: " + e.getMessage());
          // Continue listening for new connections
        } finally {
          // Clean up resources for this connection
          try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (sock != null) sock.close();
          } catch (IOException e) {
            System.out.println("Error closing connection resources: " + e.getMessage());
          }
        }
      }
    } catch (IOException e) {
      System.out.println("Could not listen on port " + port);
      System.out.println("Error: " + e.getMessage());
      System.exit(1);
    } finally {
      try {
        if (server != null && !server.isClosed()) {
          server.close();
        }
      } catch (IOException e) {
        System.out.println("Error closing server: " + e.getMessage());
      }
    }
  }

  /**
   * Used in the "/random" endpoint
   */
  private final static HashMap<String, String> _images = new HashMap<>() {
    {
      put("streets", "https://iili.io/JV1pSV.jpg");
      put("bread", "https://iili.io/Jj9MWG.jpg");
    }
  };

  private Random random = new Random();

  /**
   * Reads in socket stream and generates a response
   * @param inStream HTTP input stream from socket
   * @return the byte encoded HTTP response
   */
  public byte[] createResponse(InputStream inStream) {
    byte[] response = null;
    BufferedReader in = null;
    StringBuilder builder = new StringBuilder();

    try {
      if (inStream == null) {
        throw new IOException("Input stream is null");
      }

      in = new BufferedReader(new InputStreamReader(inStream, "UTF-8"));
      String request = null;
      boolean done = false;

      while (!done) {
        String line = in.readLine();
        if (line == null) {
          throw new IOException("Connection closed by client");
        }

        System.out.println("Received: " + line);

        if (line.equals("")) {
          done = true;
        } else if (line.startsWith("GET")) {
          try {
            int firstSpace = line.indexOf(" ");
            int secondSpace = line.indexOf(" ", firstSpace + 1);
            if (firstSpace == -1 || secondSpace == -1) {
              throw new IOException("Invalid GET request format");
            }
            request = line.substring(firstSpace + 2, secondSpace);
          } catch (Exception e) {
            throw new IOException("Error parsing GET request: " + e.getMessage());
          }
        }
      }

      System.out.println("FINISHED PARSING HEADER\n");

      if (request == null) {
        builder.append("HTTP/1.1 400 Bad Request\n");
        builder.append("Content-Type: text/html; charset=utf-8\n");
        builder.append("\n");
        builder.append("<html><body><h1>400 Bad Request</h1><p>Illegal request: no GET</p></body></html>");
        return builder.toString().getBytes();
      }

      // Handle different request types with proper error handling
      if (request.length() == 0) {
        try {
          File rootFile = new File("www/root.html");
          if (!rootFile.exists()) {
            throw new FileNotFoundException("root.html not found");
          }
          String page = new String(readFileInBytes(rootFile));
          page = page.replace("${links}", buildFileList());

          builder.append("HTTP/1.1 200 OK\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append(page);
        } catch (IOException e) {
          builder.append("HTTP/1.1 500 Internal Server Error\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append("<html><body><h1>500 Internal Server Error</h1><p>Error: " + e.getMessage() + "</p></body></html>");
        }
      } else if (request.equalsIgnoreCase("json")) {
        try {
          if (_images.isEmpty()) {
            throw new IllegalStateException("No images available");
          }
          int index = random.nextInt(_images.size());
          String header = (String) _images.keySet().toArray()[index];
          String url = _images.get(header);

          builder.append("HTTP/1.1 200 OK\n");
          builder.append("Content-Type: application/json; charset=utf-8\n");
          builder.append("\n");
          builder.append("{");
          builder.append("\"header\":\"").append(header).append("\",");
          builder.append("\"image\":\"").append(url).append("\"");
          builder.append("}");
        } catch (Exception e) {
          builder.append("HTTP/1.1 500 Internal Server Error\n");
          builder.append("Content-Type: application/json; charset=utf-8\n");
          builder.append("\n");
          builder.append("{\"error\":\"" + e.getMessage() + "\"}");
        }
      } else if (request.equalsIgnoreCase("random")) {
        try {
          File file = new File("www/index.html");
          if (!file.exists()) {
            throw new FileNotFoundException("index.html not found");
          }

          builder.append("HTTP/1.1 200 OK\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append(new String(readFileInBytes(file)));
        } catch (IOException e) {
          builder.append("HTTP/1.1 500 Internal Server Error\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append("<html><body><h1>500 Internal Server Error</h1><p>Error: " + e.getMessage() + "</p></body></html>");
        }
      } else if (request.contains("file/")) {
        try {
          String filePath = request.replace("file/", "");
          if (filePath.contains("..")) {
            throw new SecurityException("Access denied: Path traversal attempt");
          }
          File file = new File(filePath);
          if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + filePath);
          }
          if (!file.canRead()) {
            throw new SecurityException("File not readable: " + filePath);
          }

          builder.append("HTTP/1.1 200 OK\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append(new String(readFileInBytes(file)));
        } catch (SecurityException e) {
          builder.append("HTTP/1.1 403 Forbidden\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append("<html><body><h1>403 Forbidden</h1><p>Error: " + e.getMessage() + "</p></body></html>");
        } catch (FileNotFoundException e) {
          builder.append("HTTP/1.1 404 Not Found\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append("<html><body><h1>404 Not Found</h1><p>Error: " + e.getMessage() + "</p></body></html>");
        } catch (IOException e) {
          builder.append("HTTP/1.1 500 Internal Server Error\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append("<html><body><h1>500 Internal Server Error</h1><p>Error: " + e.getMessage() + "</p></body></html>");
        }
      } else if (request.startsWith("multiply")) {
        // This multiplies two numbers with proper error handling
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        try {
          // extract path parameters
          query_pairs = splitQuery(request.replace("multiply?", ""));

          // Check if both parameters are present
          if (!query_pairs.containsKey("num1") || !query_pairs.containsKey("num2")) {
            builder.append("HTTP/1.1 400 Bad Request\n");
            builder.append("Content-Type: text/html; charset=utf-8\n");
            builder.append("\n");
            builder.append("<html><body><h1>400 Bad Request</h1><p>Error: Both num1 and num2 parameters are required</p></body></html>");
            return builder.toString().getBytes();
          }

          // Try to parse the numbers
          Integer num1, num2;
          try {
            num1 = Integer.parseInt(query_pairs.get("num1"));
            num2 = Integer.parseInt(query_pairs.get("num2"));
          } catch (NumberFormatException e) {
            builder.append("HTTP/1.1 400 Bad Request\n");
            builder.append("Content-Type: text/html; charset=utf-8\n");
            builder.append("\n");
            builder.append("<html><body><h1>400 Bad Request</h1><p>Error: Both parameters must be valid integers</p></body></html>");
            return builder.toString().getBytes();
          }

          // Check for overflow
          if (num1 > 0 && num2 > 0 && num1 > Integer.MAX_VALUE / num2) {
            builder.append("HTTP/1.1 400 Bad Request\n");
            builder.append("Content-Type: text/html; charset=utf-8\n");
            builder.append("\n");
            builder.append("<html><body><h1>400 Bad Request</h1><p>Error: Result would overflow integer range</p></body></html>");
            return builder.toString().getBytes();
          }

          // do math
          Integer result = num1 * num2;

          // Generate success response
          builder.append("HTTP/1.1 200 OK\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append("<html><body><h1>Multiplication Result</h1><p>Result is: " + result + "</p></body></html>");
        } catch (UnsupportedEncodingException e) {
          builder.append("HTTP/1.1 400 Bad Request\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append("<html><body><h1>400 Bad Request</h1><p>Error: Invalid URL encoding</p></body></html>");
        }
      } else if (request.startsWith("github")) {
        // pulls the query from the request and runs it with GitHub's REST API
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        try {
          query_pairs = splitQuery(request.replace("github?", ""));
          
          if (!query_pairs.containsKey("query")) {
            builder.append("HTTP/1.1 400 Bad Request\n");
            builder.append("Content-Type: text/html; charset=utf-8\n");
            builder.append("\n");
            builder.append("<html><body><h1>400 Bad Request</h1><p>Error: 'query' parameter is required</p></body></html>");
            return builder.toString().getBytes();
          }

          String json = fetchURL("https://api.github.com/" + query_pairs.get("query"));
          
          // Check for GitHub API rate limiting
          if (json.contains("\"message\":\"API rate limit exceeded")) {
            builder.append("HTTP/1.1 429 Too Many Requests\n");
            builder.append("Content-Type: text/html; charset=utf-8\n");
            builder.append("\n");
            builder.append("<html><body><h1>429 Too Many Requests</h1><p>Error: GitHub API rate limit exceeded. Please try again later.</p></body></html>");
            return builder.toString().getBytes();
          }
          
          // Check if the response is an error message from GitHub
          if (json.contains("\"message\":\"Not Found\"")) {
            builder.append("HTTP/1.1 404 Not Found\n");
            builder.append("Content-Type: text/html; charset=utf-8\n");
            builder.append("\n");
            builder.append("<html><body><h1>404 Not Found</h1><p>Error: GitHub resource not found</p></body></html>");
            return builder.toString().getBytes();
          }

          // Check if the response is valid JSON
          if (!json.startsWith("[")) {
            builder.append("HTTP/1.1 500 Internal Server Error\n");
            builder.append("Content-Type: text/html; charset=utf-8\n");
            builder.append("\n");
            builder.append("<html><body><h1>500 Internal Server Error</h1><p>Error: Invalid response from GitHub API</p></body></html>");
            return builder.toString().getBytes();
          }

          // Parse the JSON response
          // Remove the outer brackets and split by },{
          String content = json.substring(1, json.length() - 1);
          String[] repos = content.split("},");
          
          // Build HTML response
          builder.append("HTTP/1.1 200 OK\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append("<html><head><style>");
          builder.append("body { font-family: Arial, sans-serif; margin: 20px; }");
          builder.append("h1 { color: #333; }");
          builder.append("ul { list-style-type: none; padding: 0; }");
          builder.append("li { border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }");
          builder.append("a { color: #0366d6; text-decoration: none; }");
          builder.append("a:hover { text-decoration: underline; }");
          builder.append("</style></head><body>");
          builder.append("<h1>GitHub Repositories</h1>");
          builder.append("<ul>");
          
          for (String repo : repos) {
            try {
              // Clean up the JSON string
              repo = repo.replace("}]", "").trim();
              
              // Extract repository information
              String name = extractJsonValue(repo, "name");
              String description = extractJsonValue(repo, "description");
              String htmlUrl = extractJsonValue(repo, "html_url");
              String stars = extractJsonValue(repo, "stargazers_count");
              String language = extractJsonValue(repo, "language");
              
              if (name != null && htmlUrl != null) {
                builder.append("<li>");
                builder.append("<strong><a href=\"").append(htmlUrl).append("\">").append(name).append("</a></strong><br>");
                if (description != null && !description.equals("null")) {
                  builder.append("Description: ").append(description).append("<br>");
                }
                if (language != null && !language.equals("null")) {
                  builder.append("Language: ").append(language).append("<br>");
                }
                builder.append("Stars: ").append(stars != null ? stars : "0");
                builder.append("</li>");
              }
            } catch (Exception e) {
              // Skip malformed repository entries
              continue;
            }
          }
          
          builder.append("</ul></body></html>");
        } catch (UnsupportedEncodingException e) {
          builder.append("HTTP/1.1 400 Bad Request\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append("<html><body><h1>400 Bad Request</h1><p>Error: Invalid URL encoding</p></body></html>");
        } catch (Exception e) {
          builder.append("HTTP/1.1 500 Internal Server Error\n");
          builder.append("Content-Type: text/html; charset=utf-8\n");
          builder.append("\n");
          builder.append("<html><body><h1>500 Internal Server Error</h1><p>Error: " + e.getMessage() + "</p></body></html>");
        }
      } else {
        // if the request is not recognized at all
        builder.append("HTTP/1.1 400 Bad Request\n");
        builder.append("Content-Type: text/html; charset=utf-8\n");
        builder.append("\n");
        builder.append("<html><body><h1>400 Bad Request</h1><p>I am not sure what you want me to do...</p></body></html>");
      }

      // Output
      response = builder.toString().getBytes();
    } catch (IOException e) {
      System.out.println("Error processing request: " + e.getMessage());
      builder.append("HTTP/1.1 500 Internal Server Error\n");
      builder.append("Content-Type: text/html; charset=utf-8\n");
      builder.append("\n");
      builder.append("<html><body><h1>500 Internal Server Error</h1><p>Error: " + e.getMessage() + "</p></body></html>");
    } finally {
      try {
        if (in != null) in.close();
      } catch (IOException e) {
        System.out.println("Error closing input stream: " + e.getMessage());
      }
    }

    return builder.toString().getBytes();
  }

  /**
   * Method to read in a query and split it up correctly
   * @param query parameters on path
   * @return Map of all parameters and their specific values
   * @throws UnsupportedEncodingException If the URLs aren't encoded with UTF-8
   */
  public static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
    Map<String, String> query_pairs = new LinkedHashMap<String, String>();
    // "q=hello+world%2Fme&bob=5"
    String[] pairs = query.split("&");
    // ["q=hello+world%2Fme", "bob=5"]
    for (String pair : pairs) {
      int idx = pair.indexOf("=");
      query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
          URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
    }
    // {{"q", "hello world/me"}, {"bob","5"}}
    return query_pairs;
  }

  /**
   * Builds an HTML file list from the www directory
   * @return HTML string output of file list
   */
  public static String buildFileList() {
    try {
      ArrayList<String> filenames = new ArrayList<>();
      File directoryPath = new File("www/");
      
      if (!directoryPath.exists()) {
        return "Directory not found";
      }
      if (!directoryPath.canRead()) {
        return "Directory not readable";
      }
      
      String[] files = directoryPath.list();
      if (files == null) {
        return "Error listing directory contents";
      }
      
      filenames.addAll(Arrays.asList(files));

      if (filenames.isEmpty()) {
        return "No files in directory";
      }

      StringBuilder builder = new StringBuilder();
      builder.append("<ul>\n");
      for (String filename : filenames) {
        builder.append("<li>").append(filename).append("</li>");
      }
      builder.append("</ul>\n");
      return builder.toString();
    } catch (SecurityException e) {
      return "Access denied: " + e.getMessage();
    } catch (Exception e) {
      return "Error: " + e.getMessage();
    }
  }

  /**
   * Read bytes from a file and return them in the byte array. We read in blocks
   * of 512 bytes for efficiency.
   */
  public static byte[] readFileInBytes(File f) throws IOException {
    if (!f.exists()) {
      throw new FileNotFoundException("File not found: " + f.getPath());
    }
    if (!f.canRead()) {
      throw new SecurityException("File not readable: " + f.getPath());
    }

    FileInputStream file = null;
    ByteArrayOutputStream data = null;
    try {
      file = new FileInputStream(f);
      data = new ByteArrayOutputStream(file.available());

      byte buffer[] = new byte[512];
      int numRead;
      while ((numRead = file.read(buffer)) != -1) {
        data.write(buffer, 0, numRead);
      }
      return data.toByteArray();
    } finally {
      try {
        if (file != null) file.close();
        if (data != null) data.close();
      } catch (IOException e) {
        System.out.println("Error closing file streams: " + e.getMessage());
      }
    }
  }

  /**
   *
   * a method to make a web request. Note that this method will block execution
   * for up to 20 seconds while the request is being satisfied. Better to use a
   * non-blocking request.
   * 
   * @param aUrl the String indicating the query url for the OMDb api search
   * @return the String result of the http request.
   *
   **/
  public String fetchURL(String aUrl) {
    StringBuilder sb = new StringBuilder();
    URLConnection conn = null;
    InputStreamReader in = null;
    BufferedReader br = null;
    try {
      URL url = new URL(aUrl);
      conn = url.openConnection();
      if (conn != null) {
        conn.setReadTimeout(20 * 1000); // timeout in 20 seconds
        conn.setRequestProperty("User-Agent", "Java WebServer/1.0");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
      }
      if (conn != null && conn.getInputStream() != null) {
        in = new InputStreamReader(conn.getInputStream(), Charset.defaultCharset());
        br = new BufferedReader(in);
        if (br != null) {
          String line;
          while ((line = br.readLine()) != null) {
            sb.append(line);
          }
        }
      }
    } catch (SocketTimeoutException e) {
      System.out.println("Timeout while fetching URL: " + e.getMessage());
      return "{\"message\":\"Request timeout\"}";
    } catch (Exception ex) {
      System.out.println("Exception in url request: " + ex.getMessage());
      return "{\"message\":\"" + ex.getMessage() + "\"}";
    } finally {
      try {
        if (br != null) br.close();
        if (in != null) in.close();
        if (conn != null) conn.getInputStream().close();
      } catch (IOException e) {
        System.out.println("Error closing resources: " + e.getMessage());
      }
    }
    return sb.toString();
  }

  /**
   * Helper method to extract values from JSON string
   * @param json JSON string to parse
   * @param key key to look for
   * @return value associated with the key
   */
  private String extractJsonValue(String json, String key) {
    String searchKey = "\"" + key + "\":";
    int startIndex = json.indexOf(searchKey);
    if (startIndex == -1) return null;
    
    startIndex += searchKey.length();
    if (json.charAt(startIndex) == ' ') startIndex++;
    
    if (json.charAt(startIndex) == '"') {
      // String value
      startIndex++;
      int endIndex = json.indexOf("\"", startIndex);
      return json.substring(startIndex, endIndex);
    } else {
      // Number or boolean value
      int endIndex = json.indexOf(",", startIndex);
      if (endIndex == -1) endIndex = json.indexOf("}", startIndex);
      return json.substring(startIndex, endIndex);
    }
  }
}
