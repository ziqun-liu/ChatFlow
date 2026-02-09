package cs6650.ziqunliu.chatflow.server.controller;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.logging.Logger;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import cs6650.ziqunliu.chatflow.server.service.RequestStatsService;
import cs6650.ziqunliu.chatflow.server.model.response.ErrorResponse;
import cs6650.ziqunliu.chatflow.server.model.response.SuccessResponse;

@WebServlet("/health/*")
public class HealthController extends HttpServlet {

  private static final Logger logger = Logger.getLogger(HealthController.class.getName());
  private static final Gson GSON = new Gson();

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    logger.info("GET " + req.getRequestURI());
    res.setContentType("application/json");
    res.setCharacterEncoding("UTF-8");

    String urlPath = req.getPathInfo();

    // Special endpoint for statistics
    if ("/stats".equals(urlPath)) {
      res.setStatus(HttpServletResponse.SC_OK);
      SuccessResponse stats = new SuccessResponse("OK", null);
      stats.setMessage(RequestStatsService.getStats());
      stats.setServerTimestamp(java.time.Instant.now().toString());
      res.getWriter().write(GSON.toJson(stats));
      return;
    }

    RequestStatsService.incrementGet();

    // Check if url is valid
    if (!isUrlValid(urlPath)) {
      logger.warning("Invalid URL: " + urlPath);
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      ErrorResponse error = new ErrorResponse("INVALID_URL", "invalid url");
      error.setServerTimestamp(java.time.Instant.now().toString());
      res.getWriter().write(GSON.toJson(error));
      return;
    }

    // Return 200
    res.setStatus(HttpServletResponse.SC_OK);
    SuccessResponse ok = new SuccessResponse("OK", null);
    ok.setMessage("OK");
    ok.setServerTimestamp(java.time.Instant.now().toString());
    res.getWriter().write(GSON.toJson(ok));
  }

  private boolean isUrlValid(String urlPath) {
    if (urlPath == null || urlPath.isEmpty() || "/".equals(urlPath)) {
      return true;
    }
    return "/stats".equals(urlPath);
  }
}
