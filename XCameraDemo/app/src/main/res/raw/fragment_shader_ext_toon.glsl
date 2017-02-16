#extension GL_OES_EGL_image_external : require
precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
uniform samplerExternalOES uTexture;

varying vec2 vLeftTextureCoordinate;
varying vec2 vRightTextureCoordinate;

varying vec2 vTopTextureCoordinate;
varying vec2 vTopLeftTextureCoordinate;
varying vec2 vTopRightTextureCoordinate;

varying vec2 vBottomTextureCoordinate;
varying vec2 vBottomLeftTextureCoordinate;
varying vec2 vBottomRightTextureCoordinate;

uniform float uIntensity;
uniform float uThreshold;
uniform float uQuantizationLevels;
uniform int uProcessHSV;

const highp vec3 W = vec3(0.2125, 0.7154, 0.0721);

//将RGB转为HSV
vec3 RGBtoHSV(float r, float g, float b)
{
    float minv, maxv, delta;
    vec3 res;

    minv = min(min(r, g), b);
    maxv = max(max(r, g), b);
    res.z = maxv;           // v

    delta = maxv - minv;

    if ( maxv != 0.0 )
       res.y = delta / maxv;     // s
    else {
       // r = g = b = 0     // s = 0, v is undefined
       res.y = 0.0;
       res.x = 0.0;
       return res;
    }

    if( r == maxv )
       res.x = ( g - b ) / delta;     // between yellow & magenta
    else if( g == maxv )
       res.x = 2.0 + ( b - r ) / delta;   // between cyan &yellow
    else
       res.x = 4.0 + ( r - g ) / delta;   // between magenta &cyan

    res.x = res.x * 60.0;           // degrees
    if ( res.x < 0.0 )
        res.x += 360.0;
    else if (res.x > 360.0)
        res.x -= 360.0;

    return res;
}

//HSV转RGB
vec3 HSVtoRGB(float h, float s, float v)
{
    int i;
    float f, p,q, t;
    vec3 res;

    if (s == 0.0) {
       // achromatic (grey)
       res.x = v;
       res.y = v;
       res.z = v;
       return res;
    }

    h /= 60.0;        // sector 0 to 5
    i = int(floor( h ));
    f = h - floor( h );        // factorial part of h
    p = v * (1.0 - s );
    q = v * (1.0 - s * f );
    t = v * (1.0 - s * ( 1.0 - f ) );

    if (i == 0){
       res.x = v;
       res.y = t;
       res.z = p;
    }
    else if (i ==1) {
       res.x = q;
       res.y = v;
       res.z = p;
    }
    else if (i ==2) {
       res.x = p;
       res.y = v;
       res.z = t;
    }
    else if (i ==3) {
       res.x = p;
       res.y = q;
       res.z = v;
    }
    else if (i ==4) {
       res.x = t;
       res.y = p;
       res.z = v;
    }
    else {
       res.x = v;
       res.y = p;
       res.z = q;
    }

    return res;
}

//根据参数值所在区间返回H值
float nearestHueLevel(float col)
{
    int i = int(ceil(col / 60.0));
    return 60.0 * float(i);
}

float nearestSatLevel(float col)
{
    int i = int(ceil(col * 7.0));
    return float(i) / 7.0;
}

float nearestValLevel(float col)
{
    int i = int(ceil(col * 4.0));
    return float(i) / 4.0;
}


void main() {
    vec4 textureColor = texture2D(uTexture, vTextureCoord);

    float bottomLeftIntensity = texture2D(uTexture, vBottomLeftTextureCoordinate).r;
    float topRightIntensity = texture2D(uTexture, vTopRightTextureCoordinate).r;
    float topLeftIntensity = texture2D(uTexture, vTopLeftTextureCoordinate).r;
    float bottomRightIntensity = texture2D(uTexture, vBottomRightTextureCoordinate).r;
    float leftIntensity = texture2D(uTexture, vLeftTextureCoordinate).r;
    float rightIntensity = texture2D(uTexture, vRightTextureCoordinate).r;
    float bottomIntensity = texture2D(uTexture, vBottomTextureCoordinate).r;
    float topIntensity = texture2D(uTexture, vTopTextureCoordinate).r;

    float h = -topLeftIntensity - 2.0 * topIntensity - topRightIntensity + bottomLeftIntensity + 2.0 * bottomIntensity + bottomRightIntensity;
    float v = -bottomLeftIntensity - 2.0 * leftIntensity - topLeftIntensity + bottomRightIntensity + 2.0 * rightIntensity + topRightIntensity;

    float mag = length(vec2(h, v));

    vec3 posterizedImageColor = floor((textureColor.rgb * uQuantizationLevels) + 0.5) / uQuantizationLevels;

    float thresholdTest = 1.0 - step(uThreshold, mag);

    if (uProcessHSV > 0) {
        // added
        vec3 rgb = posterizedImageColor * thresholdTest;
        vec3 vHSV = RGBtoHSV(rgb.r, rgb.g, rgb.b);
        //将HSV值变换到预先定义的区间值中
        vHSV.x = nearestHueLevel(vHSV.x);
        vHSV.y = nearestSatLevel(vHSV.y);
        vHSV.z = nearestValLevel(vHSV.z);
        vec3 vRGB = HSVtoRGB(vHSV.x, vHSV.y, vHSV.z);

        gl_FragColor = vec4(vRGB, textureColor.a);
    }
    else {
        gl_FragColor = vec4(posterizedImageColor * thresholdTest, textureColor.a);
    }
}