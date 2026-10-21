#pragma once
#import <Foundation/Foundation.h>

static BOOL ImpressIsJsonWhitespace(unichar c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
}

static BOOL ImpressIsJsonValueDelimiter(unichar c) {
    return c == ',' || c == '}' || c == ']' || c == ':';
}

static NSString *ImpressStripMarkdownFences(NSString *text) {
    NSString *clean = [text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (clean.length == 0) {
        return @"";
    }
    NSRegularExpression *fenced = [NSRegularExpression
        regularExpressionWithPattern:@"```(?:json)?\\s*([\\s\\S]*?)```"
                             options:NSRegularExpressionCaseInsensitive
                               error:nil];
    NSTextCheckingResult *match = [fenced firstMatchInString:clean options:0 range:NSMakeRange(0, clean.length)];
    if (match && match.numberOfRanges > 1) {
        clean = [clean substringWithRange:[match rangeAtIndex:1]];
        return [clean stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    }
    if ([clean hasPrefix:@"```"]) {
        NSRange newline = [clean rangeOfString:@"\n"];
        clean = newline.location != NSNotFound ? [clean substringFromIndex:newline.location + 1]
                                               : [clean substringFromIndex:MIN((NSUInteger)3, clean.length)];
        if ([clean hasSuffix:@"```"]) {
            clean = [clean substringToIndex:clean.length - 3];
        }
    }
    return [clean stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
}

// Preserve the complete outer array as well as object roots. Extracting the first
// object from a slide array silently loses all subsequent slides.
static NSString *ImpressExtractJsonRoot(NSString *text) {
    NSRange opening = [text rangeOfCharacterFromSet:[NSCharacterSet characterSetWithCharactersInString:@"{["]];
    if (opening.location == NSNotFound) return text;
    BOOL inString = NO, escape = NO;
    NSInteger depth = 0;
    for (NSUInteger i = opening.location; i < text.length; i++) {
        unichar c = [text characterAtIndex:i];
        if (inString) {
            if (escape) { escape = NO; continue; }
            if (c == '\\') { escape = YES; continue; }
            if (c == '"') inString = NO;
            continue;
        }
        if (c == '"') { inString = YES; continue; }
        if (c == '{' || c == '[') depth++;
        else if (c == '}' || c == ']') {
            depth--;
            if (depth == 0) return [text substringWithRange:NSMakeRange(opening.location, i - opening.location + 1)];
        }
    }
    return [text substringFromIndex:opening.location];
}

static NSString *ImpressRepairModelJson(NSString *text) {
    if (text.length == 0) {
        return text ?: @"";
    }
    NSMutableString *out = [NSMutableString stringWithCapacity:text.length + 32];
    BOOL inString = NO;
    BOOL escape = NO;
    for (NSUInteger i = 0; i < text.length; i++) {
        unichar c = [text characterAtIndex:i];
        if (inString) {
            if (escape) {
                // The backslash is already written. A raw break after it is not a
                // JSON escape, so finish it as \n / \t instead of leaving a raw break.
                if (c == '\n' || c == '\r') {
                    [out appendString:@"n"];
                } else if (c == '\t') {
                    [out appendString:@"t"];
                } else {
                    [out appendFormat:@"%C", c];
                }
                escape = NO;
                continue;
            }
            if (c == '\\') {
                [out appendString:@"\\"];
                escape = YES;
                continue;
            }
            if (c == '"') {
                NSUInteger j = i + 1;
                while (j < text.length && ImpressIsJsonWhitespace([text characterAtIndex:j])) {
                    j++;
                }
                unichar next = j < text.length ? [text characterAtIndex:j] : 0;
                if (j >= text.length || ImpressIsJsonValueDelimiter(next)) {
                    inString = NO;
                    [out appendString:@"\""];
                } else {
                    [out appendString:@"\\\""];
                }
                continue;
            }
            if (c == '\n') {
                [out appendString:@"\\n"];
                continue;
            }
            if (c == '\r') {
                continue;
            }
            if (c == '\t') {
                [out appendString:@"\\t"];
                continue;
            }
            if (c < 0x20) {
                continue;
            }
            [out appendFormat:@"%C", c];
            continue;
        }
        if (c == '"') {
            inString = YES;
            [out appendString:@"\""];
            continue;
        }
        if (c == ',') {
            NSUInteger j = i + 1;
            while (j < text.length && ImpressIsJsonWhitespace([text characterAtIndex:j])) {
                j++;
            }
            if (j < text.length) {
                unichar next = [text characterAtIndex:j];
                if (next == '}' || next == ']') {
                    continue;
                }
            }
        }
        [out appendFormat:@"%C", c];
    }
    return out;
}

static BOOL ImpressJsonLooksTruncated(NSString *text) {
    NSString *trim = [text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (trim.length == 0) {
        return YES;
    }
    if ([trim hasSuffix:@"..."] || [trim hasSuffix:@"…"]) {
        return YES;
    }
    BOOL inString = NO;
    BOOL escape = NO;
    NSInteger depth = 0;
    BOOL sawOpen = NO;
    for (NSUInteger i = 0; i < trim.length; i++) {
        unichar c = [trim characterAtIndex:i];
        if (inString) {
            if (escape) {
                escape = NO;
                continue;
            }
            if (c == '\\') {
                escape = YES;
                continue;
            }
            if (c == '"') {
                inString = NO;
            }
            continue;
        }
        if (c == '"') {
            inString = YES;
            continue;
        }
        if (c == '{' || c == '[') {
            depth++;
            sawOpen = YES;
        } else if (c == '}' || c == ']') {
            depth--;
        }
    }
    return inString || !sawOpen || depth != 0 || !([trim hasSuffix:@"}"] || [trim hasSuffix:@"]"]);
}

// Parse valid JSON before attempting formatting repairs; never construct a
// successful result by discarding an incomplete trailing slide.
static id ImpressParseModelJson(NSString *raw, BOOL *truncatedOut, NSError **errorOut) {
    NSString *stripped = ImpressStripMarkdownFences(raw ?: @"");
    NSData *bytes = [stripped dataUsingEncoding:NSUTF8StringEncoding];
    NSError *error = nil;
    id object = bytes ? [NSJSONSerialization JSONObjectWithData:bytes options:0 error:&error] : nil;
    if (object) {
        if (truncatedOut) *truncatedOut = NO;
        if (errorOut) *errorOut = nil;
        return object;
    }
    // A few models put visible reasoning before JSON even on their base API.
    // Strip only a leading, closed block, never tags inside valid JSON strings.
    if ([stripped hasPrefix:@"<think>"]) {
        NSRange end = [stripped rangeOfString:@"</think>"];
        if (end.location != NSNotFound) stripped = [stripped substringFromIndex:NSMaxRange(end)];
    }
    NSString *candidate = ImpressExtractJsonRoot(stripped);
    bytes = [candidate dataUsingEncoding:NSUTF8StringEncoding];
    object = bytes ? [NSJSONSerialization JSONObjectWithData:bytes options:0 error:&error] : nil;
    if (!object) {
        candidate = ImpressRepairModelJson(candidate);
        bytes = [candidate dataUsingEncoding:NSUTF8StringEncoding];
        object = bytes ? [NSJSONSerialization JSONObjectWithData:bytes options:0 error:&error] : nil;
    }
    if (truncatedOut) *truncatedOut = !object && ImpressJsonLooksTruncated(candidate);
    if (errorOut) *errorOut = object ? nil : error;
    return object;
}
