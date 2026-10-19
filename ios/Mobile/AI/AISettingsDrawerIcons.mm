// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AISettingsDrawerIcons.h"

#import "AIModelConfigStore.h"
#import "Settings/AppIcons.h"

static UIColor *AIDrawerIconColor(NSString *hex) {
    if (hex.length == 7 && [hex hasPrefix:@"#"]) {
        unsigned r = 0, g = 0, b = 0;
        NSScanner *scanner = [NSScanner scannerWithString:[hex substringFromIndex:1]];
        unsigned value = 0;
        if ([scanner scanHexInt:&value]) {
            r = (value >> 16) & 0xFF;
            g = (value >> 8) & 0xFF;
            b = value & 0xFF;
            return [UIColor colorWithRed:r / 255.0 green:g / 255.0 blue:b / 255.0 alpha:1];
        }
    }
    if (hex.length == 9 && [hex hasPrefix:@"#"]) {
        unsigned a = 0, r = 0, g = 0, b = 0;
        NSScanner *scanner = [NSScanner scannerWithString:[hex substringFromIndex:1]];
        unsigned value = 0;
        if ([scanner scanHexInt:&value]) {
            a = (value >> 24) & 0xFF;
            r = (value >> 16) & 0xFF;
            g = (value >> 8) & 0xFF;
            b = value & 0xFF;
            return [UIColor colorWithRed:r / 255.0 green:g / 255.0 blue:b / 255.0 alpha:a / 255.0];
        }
    }
    return UIColor.blackColor;
}

static void AIDrawerStrokePath(UIBezierPath *path, UIColor *color, CGFloat lineWidth) {
    path.lineWidth = lineWidth;
    path.lineCapStyle = kCGLineCapRound;
    path.lineJoinStyle = kCGLineJoinRound;
    [color setStroke];
    [path stroke];
}

static UIImage *AIDrawerRender(NSString *cacheKey, CGFloat size, void (^draw)(CGRect rect, CGFloat scale)) {
    static NSCache<NSString *, UIImage *> *cache;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        cache = [[NSCache alloc] init];
    });
    NSString *key = [NSString stringWithFormat:@"%@_%.0f", cacheKey, size];
    UIImage *cached = [cache objectForKey:key];
    if (cached) {
        return cached;
    }
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(size, size)];
    UIImage *image = [renderer imageWithActions:^(UIGraphicsImageRendererContext *ctx) {
        CGRect rect = CGRectMake(0, 0, size, size);
        CGFloat scale = size / 24.0;
        draw(rect, scale);
    }];
    if (image) {
        [cache setObject:image forKey:key];
    }
    return image;
}

@implementation AISettingsDrawerIcons

+ (UIImage *)iconNamed:(NSString *)name size:(CGFloat)size {
    UIImage *shared = [AppIcons iconNamed:name size:size];
    if (shared != nil) {
        return shared;
    }

    if ([name isEqualToString:@"chevron-right"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIBezierPath *path = [UIBezierPath bezierPath];
            [path moveToPoint:CGPointMake(9.0 * scale, 7.0 * scale)];
            [path addLineToPoint:CGPointMake(15.0 * scale, 12.0 * scale)];
            [path addLineToPoint:CGPointMake(9.0 * scale, 17.0 * scale)];
            AIDrawerStrokePath(path, AIDrawerIconColor(@"#66000000"), 2.0 * scale);
        });
    }
    if ([name isEqualToString:@"chevron-up"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIBezierPath *path = [UIBezierPath bezierPath];
            [path moveToPoint:CGPointMake(6.0 * scale, 15.0 * scale)];
            [path addLineToPoint:CGPointMake(12.0 * scale, 9.0 * scale)];
            [path addLineToPoint:CGPointMake(18.0 * scale, 15.0 * scale)];
            AIDrawerStrokePath(path, AIDrawerIconColor(@"#66000000"), 2.0 * scale);
        });
    }
    if ([name isEqualToString:@"chevron-down"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIBezierPath *path = [UIBezierPath bezierPath];
            [path moveToPoint:CGPointMake(6.0 * scale, 9.0 * scale)];
            [path addLineToPoint:CGPointMake(12.0 * scale, 15.0 * scale)];
            [path addLineToPoint:CGPointMake(18.0 * scale, 9.0 * scale)];
            AIDrawerStrokePath(path, AIDrawerIconColor(@"#66000000"), 2.0 * scale);
        });
    }
    if ([name isEqualToString:@"config-back"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIBezierPath *path = [UIBezierPath bezierPath];
            [path moveToPoint:CGPointMake(14.5 * scale, 7.0 * scale)];
            [path addLineToPoint:CGPointMake(8.5 * scale, 12.0 * scale)];
            [path addLineToPoint:CGPointMake(14.5 * scale, 17.0 * scale)];
            AIDrawerStrokePath(path, AIDrawerIconColor(@"#e6000000"), 2.0 * scale);
        });
    }
    if ([name isEqualToString:@"profile-edit"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIBezierPath *frame = [UIBezierPath bezierPath];
            [frame moveToPoint:CGPointMake(18.0 * scale, 12.5 * scale)];
            [frame addLineToPoint:CGPointMake(18.0 * scale, 17.5 * scale)];
            [frame addLineToPoint:CGPointMake(6.5 * scale, 17.5 * scale)];
            [frame addLineToPoint:CGPointMake(6.5 * scale, 6.5 * scale)];
            [frame addLineToPoint:CGPointMake(11.5 * scale, 6.5 * scale)];
            AIDrawerStrokePath(frame, AIDrawerIconColor(@"#101010"), 1.2 * scale);
            UIBezierPath *pencil = [UIBezierPath bezierPath];
            [pencil moveToPoint:CGPointMake(8.5 * scale, 13.0 * scale)];
            [pencil addLineToPoint:CGPointMake(10.8 * scale, 15.3 * scale)];
            [pencil addLineToPoint:CGPointMake(17.0 * scale, 9.1 * scale)];
            [pencil addLineToPoint:CGPointMake(14.7 * scale, 6.8 * scale)];
            [pencil closePath];
            AIDrawerStrokePath(pencil, AIDrawerIconColor(@"#101010"), 1.2 * scale);
        });
    }
    if ([name isEqualToString:@"ai-connection"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIColor *orange = AIDrawerIconColor(@"#fa6200");
            CGFloat factor = scale * 0.6;
            UIBezierPath *link = [UIBezierPath bezierPath];
            [link moveToPoint:CGPointMake(30.001 * factor, 6.667 * factor)];
            [link addLineToPoint:CGPointMake(10.834 * factor, 6.667 * factor)];
            [link addCurveToPoint:CGPointMake(3.334 * factor, 13.333 * factor)
                    controlPoint1:CGPointMake(8.334 * factor, 6.667 * factor)
                    controlPoint2:CGPointMake(3.334 * factor, 8.333 * factor)];
            [link addCurveToPoint:CGPointMake(10.834 * factor, 20.0 * factor)
                    controlPoint1:CGPointMake(3.334 * factor, 18.333 * factor)
                    controlPoint2:CGPointMake(8.334 * factor, 20.0 * factor)];
            [link addLineToPoint:CGPointMake(29.167 * factor, 20.0 * factor)];
            [link addCurveToPoint:CGPointMake(36.667 * factor, 26.667 * factor)
                    controlPoint1:CGPointMake(31.667 * factor, 20.0 * factor)
                    controlPoint2:CGPointMake(36.667 * factor, 21.667 * factor)];
            [link addCurveToPoint:CGPointMake(29.167 * factor, 33.333 * factor)
                    controlPoint1:CGPointMake(36.667 * factor, 31.667 * factor)
                    controlPoint2:CGPointMake(31.667 * factor, 33.333 * factor)];
            [link addLineToPoint:CGPointMake(10.001 * factor, 33.333 * factor)];
            AIDrawerStrokePath(link, orange, 3.0 * factor);

            UIBezierPath *topNode = [UIBezierPath bezierPathWithOvalInRect:
                CGRectMake((33.333 - 3.333) * factor, (6.666 - 3.333) * factor,
                           6.666 * factor, 6.666 * factor)];
            AIDrawerStrokePath(topNode, orange, 3.0 * factor);
            UIBezierPath *bottomNode = [UIBezierPath bezierPathWithOvalInRect:
                CGRectMake((6.667 - 3.333) * factor, (33.333 - 3.333) * factor,
                           6.666 * factor, 6.666 * factor)];
            AIDrawerStrokePath(bottomNode, orange, 3.0 * factor);
        });
    }
    if ([name isEqualToString:@"ai-params"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIColor *orange = AIDrawerIconColor(@"#fa6200");
            CGFloat factor = scale * 0.6;
            UIBezierPath *wrench = [UIBezierPath bezierPath];
            [wrench moveToPoint:CGPointMake(36.6673 * factor, 13.3334 * factor)];
            [wrench addCurveToPoint:CGPointMake(26.6673 * factor, 23.3334 * factor)
                      controlPoint1:CGPointMake(36.6673 * factor, 18.8562 * factor)
                      controlPoint2:CGPointMake(32.1901 * factor, 23.3334 * factor)];
            [wrench addCurveToPoint:CGPointMake(21.992 * factor, 22.1754 * factor)
                      controlPoint1:CGPointMake(24.9784 * factor, 23.3334 * factor)
                      controlPoint2:CGPointMake(23.3872 * factor, 22.9146 * factor)];
            [wrench addLineToPoint:CGPointMake(9.584 * factor, 34.5834 * factor)];
            [wrench addCurveToPoint:CGPointMake(5.4173 * factor, 34.5834 * factor)
                      controlPoint1:CGPointMake(8.4334 * factor, 35.734 * factor)
                      controlPoint2:CGPointMake(6.5679 * factor, 35.734 * factor)];
            [wrench addCurveToPoint:CGPointMake(5.4173 * factor, 30.4167 * factor)
                      controlPoint1:CGPointMake(4.2667 * factor, 33.4328 * factor)
                      controlPoint2:CGPointMake(4.2667 * factor, 31.5673 * factor)];
            [wrench addLineToPoint:CGPointMake(17.8253 * factor, 18.0087 * factor)];
            [wrench addCurveToPoint:CGPointMake(16.6673 * factor, 13.3334 * factor)
                      controlPoint1:CGPointMake(17.0861 * factor, 16.6135 * factor)
                      controlPoint2:CGPointMake(16.6673 * factor, 15.0223 * factor)];
            [wrench addCurveToPoint:CGPointMake(26.6673 * factor, 3.3334 * factor)
                      controlPoint1:CGPointMake(21.1445 * factor, 3.3334 * factor)
                      controlPoint2:CGPointMake(26.6673 * factor, 7.8105 * factor)];
            [wrench addCurveToPoint:CGPointMake(31.3427 * factor, 4.4914 * factor)
                      controlPoint1:CGPointMake(28.3562 * factor, 3.3334 * factor)
                      controlPoint2:CGPointMake(29.9474 * factor, 3.7521 * factor)];
            [wrench addLineToPoint:CGPointMake(25.0007 * factor, 10.8334 * factor)];
            [wrench addLineToPoint:CGPointMake(29.1673 * factor, 15.0 * factor)];
            [wrench addLineToPoint:CGPointMake(35.5093 * factor, 8.658 * factor)];
            [wrench addCurveToPoint:CGPointMake(36.6673 * factor, 13.3334 * factor)
                      controlPoint1:CGPointMake(36.2486 * factor, 10.0533 * factor)
                      controlPoint2:CGPointMake(36.6673 * factor, 11.6445 * factor)];
            [wrench closePath];
            AIDrawerStrokePath(wrench, orange, 3.0 * factor);
        });
    }
    if ([name isEqualToString:@"model-vision"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIColor *gray = AIDrawerIconColor(@"#6a6a6a");
            void (^corner)(CGFloat, CGFloat, CGFloat, CGFloat) = ^(CGFloat x1, CGFloat y1, CGFloat x2, CGFloat y2) {
                UIBezierPath *path = [UIBezierPath bezierPath];
                [path moveToPoint:CGPointMake(x1 * scale, y1 * scale)];
                [path addLineToPoint:CGPointMake(x2 * scale, y2 * scale)];
                AIDrawerStrokePath(path, gray, 1.3 * scale);
            };
            corner(9, 3.75, 4.5, 3.75);
            corner(4.5, 3.75, 4.5, 9.25);
            corner(15, 3.75, 19.5, 3.75);
            corner(19.5, 3.75, 19.5, 9.25);
            corner(9, 20.75, 4.5, 20.75);
            corner(4.5, 20.75, 4.5, 15.25);
            corner(15, 20.75, 19.5, 20.75);
            corner(19.5, 20.75, 19.5, 15.25);
            UIBezierPath *eye = [UIBezierPath bezierPath];
            [eye moveToPoint:CGPointMake(6.0 * scale, 12.25 * scale)];
            [eye addQuadCurveToPoint:CGPointMake(18.0 * scale, 12.25 * scale)
                        controlPoint:CGPointMake(12.0 * scale, 16.0 * scale)];
            [eye addQuadCurveToPoint:CGPointMake(6.0 * scale, 12.25 * scale)
                        controlPoint:CGPointMake(12.0 * scale, 8.5 * scale)];
            AIDrawerStrokePath(eye, gray, 1.3 * scale);
            UIBezierPath *pupil = [UIBezierPath bezierPathWithArcCenter:CGPointMake(12.0 * scale, 12.25 * scale)
                                                                 radius:1.5 * scale
                                                             startAngle:0
                                                               endAngle:(CGFloat)(2 * M_PI)
                                                              clockwise:YES];
            AIDrawerStrokePath(pupil, gray, 1.3 * scale);
        });
    }
    if ([name isEqualToString:@"model-local-inference"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIColor *gray = AIDrawerIconColor(@"#6a6a6a");
            CGFloat factor = scale / 2.0;
            UIBezierPath *(^circle)(CGFloat, CGFloat, CGFloat) = ^UIBezierPath *(CGFloat x, CGFloat y, CGFloat radius) {
                return [UIBezierPath bezierPathWithOvalInRect:CGRectMake((x - radius) * factor,
                                                                        (y - radius) * factor,
                                                                        radius * 2.0 * factor,
                                                                        radius * 2.0 * factor)];
            };

            AIDrawerStrokePath(circle(23.9265, 10.8415, 4.2683), gray, 2.6 * factor);
            [gray setFill];
            [circle(23.9265, 24.5002, 4.2683) fill];

            UIBezierPath *links = [UIBezierPath bezierPath];
            [links moveToPoint:CGPointMake(36.7313 * factor, 16.8171 * factor)];
            [links addLineToPoint:CGPointMake(13.0 * factor, 32.3774 * factor)];
            [links moveToPoint:CGPointMake(13.2637 * factor, 19.2804 * factor)];
            [links addLineToPoint:CGPointMake(31.7472 * factor, 28.5632 * factor)];
            [links moveToPoint:CGPointMake(23.9277 * factor, 15.1099 * factor)];
            [links addLineToPoint:CGPointMake(23.9277 * factor, 39.0123 * factor)];
            AIDrawerStrokePath(links, gray, 2.0 * factor);

            AIDrawerStrokePath(circle(10.2683, 33.7683, 4.2683), gray, 2.6 * factor);
            AIDrawerStrokePath(circle(36.2763, 30.7759, 4.2683), gray, 2.6 * factor);
            [circle(38.0, 16.5, 4.0) fill];
            [circle(23.9258, 39.0122, 4.0) fill];
            [circle(13.2644, 19.2803, 4.0) fill];
        });
    }
    return nil;
}

+ (NSString *)modelIconNameForType:(NSInteger)modelType {
    switch ((AIModelType)modelType) {
        case AIModelTypeThink:
            return @"model-think";
        case AIModelTypeImage:
            return @"model-image";
        case AIModelTypeVision:
            return @"model-vision";
        case AIModelTypeBase:
        default:
            return @"model-base";
    }
}

@end
