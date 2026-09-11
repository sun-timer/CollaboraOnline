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
            UIBezierPath *link = [UIBezierPath bezierPath];
            [link moveToPoint:CGPointMake(18.0 * scale, 4.0 * scale)];
            [link addLineToPoint:CGPointMake(6.5 * scale, 4.0 * scale)];
            [link addCurveToPoint:CGPointMake(2.0 * scale, 8.0 * scale)
                     controlPoint1:CGPointMake(4.0 * scale, 4.0 * scale)
                     controlPoint2:CGPointMake(2.0 * scale, 6.0 * scale)];
            [link addLineToPoint:CGPointMake(2.0 * scale, 12.0 * scale)];
            [link addCurveToPoint:CGPointMake(6.5 * scale, 16.0 * scale)
                     controlPoint1:CGPointMake(2.0 * scale, 14.0 * scale)
                     controlPoint2:CGPointMake(4.0 * scale, 16.0 * scale)];
            [link addLineToPoint:CGPointMake(17.5 * scale, 16.0 * scale)];
            [link addCurveToPoint:CGPointMake(22.0 * scale, 12.0 * scale)
                     controlPoint1:CGPointMake(20.0 * scale, 16.0 * scale)
                     controlPoint2:CGPointMake(22.0 * scale, 14.0 * scale)];
            [link addLineToPoint:CGPointMake(22.0 * scale, 8.0 * scale)];
            AIDrawerStrokePath(link, orange, 1.5 * scale);
        });
    }
    if ([name isEqualToString:@"ai-params"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIColor *orange = AIDrawerIconColor(@"#fa6200");
            UIBezierPath *wrench = [UIBezierPath bezierPath];
            [wrench moveToPoint:CGPointMake(18.0 * scale, 6.5 * scale)];
            [wrench addLineToPoint:CGPointMake(6.0 * scale, 18.5 * scale)];
            [wrench moveToPoint:CGPointMake(16.0 * scale, 4.5 * scale)];
            [wrench addLineToPoint:CGPointMake(19.5 * scale, 8.0 * scale)];
            [wrench moveToPoint:CGPointMake(10.0 * scale, 10.0 * scale)];
            [wrench addArcWithCenter:CGPointMake(15.0 * scale, 15.0 * scale)
                              radius:4.0 * scale
                          startAngle:(CGFloat)(-M_PI * 0.75)
                            endAngle:(CGFloat)(M_PI * 0.25)
                           clockwise:YES];
            AIDrawerStrokePath(wrench, orange, 1.5 * scale);
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
