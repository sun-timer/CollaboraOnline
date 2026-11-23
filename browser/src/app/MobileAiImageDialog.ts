/* iOS image generation: four real candidates, preview and selected-image insertion. */
class MobileAiImageDialog {
	private readonly bridge: MobileAiBridge;
	private readonly sheet: MobileAiSheet;
	private readonly content = document.createElement('div');
	private readonly card = document.createElement('div');
	private readonly description = document.createElement('textarea');
	private readonly ratio = document.createElement('select');
	private readonly gallery = document.createElement('div');
	private readonly fullPreview = document.createElement('div');
	private readonly fullImage = document.createElement('img');
	private readonly pages = document.createElement('div');
	private readonly status = document.createElement('div');
	private readonly generateButton = document.createElement('button');
	private readonly regenerateButton = document.createElement('button');
	private readonly stopButton = document.createElement('button');
	private readonly insertButton = document.createElement('button');
	private readonly unsubscribe: () => void;
	private requestId = '';
	private resultRequestId = '';
	private documentSessionId = '';
	private images: string[] = [];
	private selected = 0;
	private enlarged = false;
	private disposed = false;

	static buildPayload(description: string, ratio = '9:16'): { [key: string]: any } {
		const sizes: Record<string, string> = { '9:16': '576x1024', '1:1': '1024x1024', '16:9': '1024x576' };
		return {taskType: 'image_generate', selection: description.trim(), context: {}, size: sizes[ratio] || sizes['9:16'], n: 4};
	}

	constructor() {
		this.bridge = MobileAiBridge.getInstance();
		this.sheet = new MobileAiSheet({title:'AI 图片', presentation:'writer', taskType:'image_generate', onClose:()=>this.dispose()});
		this.sheet.root.classList.add('mobile-ai-sheet-root--image-design');
		this.sheet.root.querySelector('.mobile-ai-sheet-close')!.innerHTML = MOBILE_AI_IMAGE_CLOSE;
		this.content.className = 'mobile-ai-image-design';
		this.card.className = 'mobile-ai-image-card';
		this.description.className = 'mobile-ai-image-prompt';
		this.description.placeholder = '描述你想生成的图片';
		this.description.setAttribute('aria-label','图片描述');
		this.card.appendChild(this.description);
		const ratioLabel = document.createElement('label'); ratioLabel.className = 'mobile-ai-image-ratio';
		ratioLabel.appendChild(document.createTextNode('比例'));
		for (const value of ['9:16','1:1','16:9']) { const option=document.createElement('option');option.value=value;option.textContent=value;this.ratio.appendChild(option); }
		this.ratio.setAttribute('aria-label','图片比例'); ratioLabel.appendChild(this.ratio);
		this.card.appendChild(ratioLabel);
		this.gallery.className = 'mobile-ai-image-gallery'; this.card.appendChild(this.gallery);
		this.content.appendChild(this.card);
		this.fullPreview.className = 'mobile-ai-image-full';
		const back=document.createElement('button');back.type='button';back.className='mobile-ai-image-back';back.setAttribute('aria-label','返回图片列表');back.innerHTML=MOBILE_AI_IMAGE_BACK;
		back.onclick=()=>{this.enlarged=false;this.render();};
		this.fullImage.alt='生成图片预览';
		const picture=document.createElement('div');picture.className='mobile-ai-image-full-picture';
		this.pages.className='mobile-ai-image-pages';picture.append(this.fullImage,this.pages);
		let start=0;picture.addEventListener('touchstart',event=>{start=event.touches[0].clientX;},{passive:true});
		picture.addEventListener('touchend',event=>{const delta=event.changedTouches[0].clientX-start;if(Math.abs(delta)>40){this.selected=(this.selected+(delta<0?1:this.images.length-1))%this.images.length;this.render();}},{passive:true});
		this.fullPreview.append(back,picture);this.content.appendChild(this.fullPreview);
		this.status.className='mobile-ai-image-status';this.status.setAttribute('role','status');this.content.appendChild(this.status);
		const actions=document.createElement('div');actions.className='mobile-ai-image-actions';
		for(const button of [this.generateButton,this.regenerateButton,this.stopButton,this.insertButton]) {button.type='button';actions.appendChild(button);}
		this.generateButton.className='mobile-ai-design-primary';this.generateButton.innerHTML='<span class="mobile-ai-design-sparkle">'+MOBILE_AI_IMAGE_SPARKLE+'</span><span>开始生成</span>';
		this.regenerateButton.textContent='重新生成';this.regenerateButton.className='mobile-ai-design-secondary';
		this.stopButton.textContent='停止生成';this.stopButton.className='mobile-ai-design-secondary';
		this.insertButton.textContent='插入文档';this.insertButton.className='mobile-ai-design-primary';
		this.generateButton.onclick=()=>this.generate();this.regenerateButton.onclick=()=>this.generate();this.stopButton.onclick=()=>this.stop();this.insertButton.onclick=()=>this.insert();
		this.content.appendChild(actions);this.sheet.setBody(this.content);
		this.unsubscribe=this.bridge.subscribe(message=>this.handleMessage(message));
	}

	open(): void {this.sheet.open();this.render();}
	close(): void {this.sheet.close();this.dispose();}
	private dispose(): void {if(this.disposed)return;this.disposed=true;if(this.requestId)this.bridge.cancel(this.requestId);if(this.resultRequestId)this.bridge.cancel(this.resultRequestId);this.resultRequestId='';this.requestId='';this.unsubscribe();}
	private generate(): void {
		const payload=MobileAiImageDialog.buildPayload(this.description.value,this.ratio.value);
		if(!payload.selection){this.status.textContent='请输入图片描述';return;}
		if(this.requestId)this.bridge.cancel(this.requestId);
		if(this.resultRequestId)this.bridge.cancel(this.resultRequestId);this.resultRequestId='';
		this.images=[];this.selected=0;this.enlarged=false;
		this.documentSessionId=NativeBridge.getInstance().getDocumentSessionId();
		this.status.textContent='正在生成 4 张图片…';this.requestId=this.bridge.request(payload);this.render();
	}
	private stop(): void {if(this.requestId){this.bridge.cancel(this.requestId);this.requestId='';this.status.textContent='已停止生成';this.render();}}
	private insert(): void {
		if(!this.images[this.selected])return;
		if(this.documentSessionId!==NativeBridge.getInstance().getDocumentSessionId()){this.status.textContent='文档已变化，请重新生成';return;}
		const result=WriterEditorController.getInstance().insertImage('ai-image.png',this.images[this.selected]);
		if(result.dispatched==='message'){if(this.resultRequestId)this.bridge.accept(this.resultRequestId,'AI 图片');this.resultRequestId='';this.close();}else this.status.textContent='插入图片失败';
	}
	private handleMessage(message: NativeBridgeEnvelope): void {
		if(this.disposed||!this.requestId||message.requestId!==this.requestId||this.documentSessionId!==NativeBridge.getInstance().getDocumentSessionId()||
			(message.documentSessionId&&message.documentSessionId!==this.documentSessionId))return;
		const payload=message.payload||{};
		if(message.type==='ai.done'){
			const candidates=Array.isArray(payload.images)?payload.images:typeof payload.imageBase64==='string'?[payload.imageBase64]:[];
			this.images=candidates.filter((value:any)=>typeof value==='string'&&value.length>0).slice(0,4);
			this.resultRequestId=this.requestId;this.requestId='';this.selected=0;this.status.textContent=payload.warning||(this.images.length===4?'':this.images.length?'实际生成 '+this.images.length+' 张图片':'未返回可用图片');
		}else if(message.type==='ai.error'||message.type==='native.error'){
			this.requestId='';this.status.textContent=payload.message||'图片生成失败';
		}else if(message.type==='ai.state'&&payload.state==='cancelled'){this.requestId='';this.status.textContent='已停止生成';}
		this.render();
	}
	private render(): void {
		const busy=!!this.requestId,ready=this.images.length>0;
		this.content.classList.toggle('mobile-ai-image-design--ready',ready);
		this.card.hidden=this.enlarged;this.fullPreview.hidden=!this.enlarged;
		this.gallery.hidden=!ready;this.gallery.dataset.count=String(this.images.length);this.gallery.replaceChildren();
		this.images.forEach((value,index)=>{
			const button=document.createElement('button');button.type='button';button.className='mobile-ai-image-candidate';button.setAttribute('aria-label','放大第 '+(index+1)+' 张图片');
			const img=document.createElement('img');img.src='data:image/png;base64,'+value;img.alt='AI 生成图片 '+(index+1);button.appendChild(img);
			button.onclick=()=>{this.selected=index;this.enlarged=true;this.render();};this.gallery.appendChild(button);
		});
		if(ready)this.fullImage.src='data:image/png;base64,'+this.images[this.selected];
		this.pages.replaceChildren();this.images.forEach((_value,index)=>{const dot=document.createElement('button');dot.type='button';dot.className='mobile-ai-image-page-dot';dot.setAttribute('aria-label','第 '+(index+1)+' 张');dot.setAttribute('aria-pressed',String(index===this.selected));dot.onclick=()=>{this.selected=index;this.render();};this.pages.appendChild(dot);});
		this.generateButton.hidden=ready||busy;this.stopButton.hidden=!busy;this.regenerateButton.hidden=!ready||busy;this.insertButton.hidden=!ready||!this.enlarged;
		this.description.disabled=busy;this.ratio.disabled=busy;this.sheet.root.classList.toggle('mobile-ai-image-enlarged',this.enlarged);
	}
}

if(typeof window!=='undefined')(window as any).MobileAiImageDialog=MobileAiImageDialog;

const MOBILE_AI_IMAGE_CLOSE = "<svg width=\"80\" height=\"80\" viewBox=\"0 0 80 80\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<g clip-path=\"url(#clip0_330_34786)\">\n<path d=\"M55.5595 26.56C55.8395 26.24 55.9795 25.88 55.9795 25.48C55.9795 25.08 55.8395 24.7333 55.5595 24.44C55.2662 24.12 54.9129 23.96 54.4995 23.96C54.0862 23.96 53.7329 24.12 53.4395 24.44L39.9995 37.88L26.5595 24.44C26.2662 24.12 25.9129 23.96 25.4995 23.96C25.0862 23.96 24.7195 24.12 24.3995 24.44C24.1462 24.7333 24.0195 25.08 24.0195 25.48C24.0195 25.88 24.1462 26.24 24.3995 26.56L37.8795 40L24.3995 53.4C24.1462 53.72 24.0195 54.0866 24.0195 54.5C24.0195 54.9133 24.1462 55.2666 24.3995 55.56C24.7195 55.8133 25.0862 55.9466 25.4995 55.96C25.9129 55.9733 26.2662 55.84 26.5595 55.56L39.9995 42.12L53.4395 55.56C53.7329 55.84 54.0862 55.98 54.4995 55.98C54.9129 55.98 55.2662 55.84 55.5595 55.56C55.8395 55.2666 55.9795 54.9133 55.9795 54.5C55.9795 54.0866 55.8395 53.72 55.5595 53.4L42.1195 40L55.5595 26.56Z\" fill=\"black\" fill-opacity=\"0.901961\"/>\n</g>\n<defs>\n<clipPath id=\"clip0_330_34786\">\n<rect width=\"48\" height=\"48\" fill=\"white\" transform=\"translate(16 16)\"/>\n</clipPath>\n</defs>\n</svg>";

const MOBILE_AI_IMAGE_BACK = "<svg width=\"48\" height=\"48\" viewBox=\"0 0 48 48\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<path d=\"M29.2552 42.6523L29.159 42.5616L14.3098 27.7123C12.3022 25.7048 12.2604 22.4759 14.1843 20.4175L14.3098 20.2877L29.159 5.43845C29.5456 5.05183 30.0379 4.83533 30.5429 4.78894L30.6809 4.7805L30.8191 4.7805C31.3717 4.79737 31.9192 5.01669 32.341 5.43845C33.1883 6.28575 33.2186 7.64069 32.4318 8.52425L32.341 8.62043L18.5524 22.409C17.7051 23.2563 17.6749 24.6113 18.4616 25.4948L18.5524 25.591L32.341 39.3796C33.2197 40.2583 33.2197 41.6829 32.341 42.5616C31.9173 42.9852 31.3668 43.2046 30.8117 43.2197L30.6729 43.2193C30.1641 43.2019 29.6602 43.0129 29.2552 42.6523Z\" fill=\"#333333\"/>\n</svg>";

const MOBILE_AI_IMAGE_SPARKLE = "<svg width=\"40\" height=\"40\" viewBox=\"0 0 40 40\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<path d=\"M21.6123 7.86523C21.7032 11.2907 23.1059 14.5532 25.5312 16.9785C28.0425 19.49 31.4484 20.9011 35 20.9014V24.1074H34.9912V24.1182C31.4368 24.1182 28.0316 25.5321 25.5225 28.041C23.0234 30.5401 21.6095 33.9456 21.6094 37.5H18.3916C18.3916 33.9454 16.9779 30.5394 14.4688 28.0303C11.9667 25.5281 8.57646 24.118 5.03906 24.1074C5.0293 24.1074 5.01953 24.1084 5.00977 24.1084C5.00651 24.1084 5.00326 24.1074 5 24.1074V20.8916C5.00326 20.8916 5.00651 20.8916 5.00977 20.8916C8.56436 20.8916 11.9694 19.4779 14.4785 16.9688C16.9052 14.5423 18.3035 11.2807 18.3955 7.85938C18.3931 7.74313 18.3916 7.62659 18.3916 7.50977H18.4014C18.4014 7.50651 18.4014 7.50326 18.4014 7.5H21.6182C21.618 7.62207 21.615 7.7438 21.6123 7.86523Z\" fill=\"white\"/>\n<path d=\"M33.3057 0.0825639C33.3261 1.83153 34.0297 3.50329 35.2659 4.73944C36.5214 5.99492 38.2244 6.70076 40 6.70099V8.303H39.9947V8.30833C38.2176 8.30838 36.5151 9.0156 35.2606 10.2699C34.011 11.5195 33.3043 13.2227 33.3043 15H31.6957L31.6877 14.6671C31.6054 13.011 30.9102 11.4407 29.7341 10.2646C28.4848 9.01531 26.7927 8.31017 25.0266 8.303C25.0195 8.30302 25.0124 8.30433 25.0053 8.30433C25.0036 8.30433 25.0018 8.303 25 8.303V6.69567C25.0018 6.69567 25.0036 6.69566 25.0053 6.69567L25.3369 6.68768C26.9931 6.60542 28.5633 5.91029 29.7394 4.73411C30.9689 3.50458 31.6682 1.84622 31.697 0.110529C31.6966 0.0751531 31.6957 0.0394767 31.6957 0.00399503H31.701V0H33.3083C33.3083 0.0275626 33.306 0.0550652 33.3057 0.0825639Z\" fill=\"white\"/>\n</svg>";
