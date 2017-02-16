precision highp float; //指定默认精度

varying vec2 vTextureCoord;
uniform sampler2D uTexture;

// https://www.shadertoy.com/view/XtBSzy
uniform vec3 uiResolution;
float c = 32.0;//amout of blocks = c*iResolution.x

void main() {
    vec2 fragCoord = vTextureCoord;

     //blocked pixel coordinate
    vec2 middle = floor(fragCoord*c+.5)/c;

    vec3 color = texture2D(uTexture,middle).rgb;

    //lego block effects
    //stud
    float dis = distance(fragCoord,middle)*c*2.;
    if(dis<.65&&dis>.55){
        color *= dot(vec2(0.707),normalize(fragCoord-middle))*.5+1.;
    }

    //side shadow
    vec2 delta = abs(fragCoord-middle)*c*2.;
    float sdis = max(delta.x,delta.y);
    if(sdis>.9){
        color *= .8;
    }

    gl_FragColor = vec4(color,1.0);
}