package com.linkforge.urlshortener.controller;

import com.linkforge.urlshortener.entity.UrlMapping;
import com.linkforge.urlshortener.exception.UrlExpiredException;
import com.linkforge.urlshortener.service.UrlService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Handles the actual short-link redirect, e.g. GET /aZ3xQ1p -> 302 to the original URL.
 * The path pattern only matches a single, alias-shaped path segment so it never
 * intercepts static assets (which live under /assets/**) or the /api/** routes.
 *
 * Password-protected links are not redirected directly (a GET can't carry a
 * password safely); instead this serves a small unlock page whose JS calls
 * POST /api/urls/{shortCode}/unlock and then navigates to the real URL client-side.
 */
@RestController
public class RedirectController {

    private final UrlService urlService;

    public RedirectController(UrlService urlService) {
        this.urlService = urlService;
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9_-]{3,20}}")
    public ResponseEntity<?> redirect(@PathVariable String shortCode) {
        UrlMapping mapping = urlService.getRawByShortCode(shortCode);

        if (mapping.isExpired()) {
            throw new UrlExpiredException(shortCode);
        }

        if (mapping.isPasswordProtected()) {
            String html = buildUnlockPage(shortCode);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        }

        String originalUrl = urlService.resolveAndTrack(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }

    private String buildUnlockPage(String shortCode) {
        String safeCode = HtmlUtils.htmlEscape(shortCode);
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>Protected link — LinkForge</title>"
                + "<link rel=\"icon\" type=\"image/svg+xml\" href=\"/assets/favicon.svg\">"
                + "<link href=\"https://fonts.googleapis.com/css2?family=Big+Shoulders+Display:wght@700;800&family=Inter:wght@400;500;600&display=swap\" rel=\"stylesheet\">"
                + "<style>"
                + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#1b1816;color:#f2ece3;font-family:'Inter',sans-serif;}"
                + ".card{background:#241f1c;border:1px solid #453c35;border-radius:6px;padding:32px;width:320px;text-align:center;}"
                + "h1{font-family:'Big Shoulders Display',sans-serif;font-size:26px;margin:0 0 6px;}"
                + "p{color:#b9afa5;font-size:14px;margin:0 0 20px;}"
                + "input{width:100%;box-sizing:border-box;background:#141210;border:1px solid #453c35;border-radius:4px;"
                + "color:#f2ece3;padding:12px 14px;font-size:15px;margin-bottom:14px;}"
                + "input:focus{outline:2px solid #e8823d;outline-offset:1px;border-color:#e8823d;}"
                + "button{width:100%;background:#e8823d;color:#141210;border:none;border-radius:4px;"
                + "padding:12px 14px;font-weight:600;font-size:15px;cursor:pointer;}"
                + "button:hover{background:#f0a066;}"
                + "button:disabled{opacity:.6;cursor:progress;}"
                + ".err{color:#d9705f;font-size:13px;min-height:18px;margin-top:12px;}"
                + "svg{color:#e8823d;width:28px;height:28px;margin-bottom:10px;}"
                + "</style></head><body>"
                + "<div class=\"card\">"
                + "<svg viewBox=\"0 0 24 24\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">"
                + "<rect x=\"5\" y=\"11\" width=\"14\" height=\"9\" rx=\"2\" stroke=\"currentColor\" stroke-width=\"2\"/>"
                + "<path d=\"M8 11V8a4 4 0 0 1 8 0v3\" stroke=\"currentColor\" stroke-width=\"2\"/></svg>"
                + "<h1>This link is protected</h1>"
                + "<p>Enter the password to continue to the destination.</p>"
                + "<form id=\"f\">"
                + "<input type=\"password\" id=\"pw\" placeholder=\"Password\" autofocus required>"
                + "<button type=\"submit\" id=\"btn\">Unlock</button>"
                + "<div class=\"err\" id=\"err\"></div>"
                + "</form>"
                + "</div>"
                + "<script>"
                + "const code=" + toJsString(safeCode) + ";"
                + "document.getElementById('f').addEventListener('submit', async function(e){"
                + "e.preventDefault();"
                + "const btn=document.getElementById('btn');const err=document.getElementById('err');"
                + "err.textContent='';btn.disabled=true;btn.textContent='Checking…';"
                + "try{"
                + "const res=await fetch('/api/urls/'+encodeURIComponent(code)+'/unlock',{"
                + "method:'POST',headers:{'Content-Type':'application/json'},"
                + "body:JSON.stringify({password:document.getElementById('pw').value})});"
                + "const data=await res.json();"
                + "if(!res.ok){throw new Error(data.message||'Incorrect password');}"
                + "window.location.href=data.originalUrl;"
                + "}catch(ex){err.textContent=ex.message;btn.disabled=false;btn.textContent='Unlock';}"
                + "});"
                + "</script></body></html>";
    }

    private String toJsString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
