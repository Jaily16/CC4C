<template>
	<div class="login-register">
		<div class="contain">
			<div class="big-box" :class="{active:isLogin}">
				<div class="big-contain" key="bigContainLogin" v-if="isLogin">
					<el-image 
						style="width: 280px;
						height: 140px" 
						:src="picURL" 
						:fit="picformat" 
					/>					
					<el-form 
						:label-position="labelPosition" 
						label-width="100px" 
						:model="loginForm" 
						style="max-width: 180px"
					>
						<el-form-item label="账号：">
							<el-input 
								v-model="loginForm.email" 
								placeholder = "电子邮箱"
							/>
						</el-form-item>
						<el-form-item label="密码：">
							<el-input 
								v-model="loginForm.password" 
								type = "password" 
								placeholder = "密码"
								show-password
							/>
						</el-form-item>
					</el-form>
					<el-button 
						type = "primary" 
						size ="large" 
						round 
						@click="login"
					>登陆</el-button>
				</div>
				<div class="big-contain" key="bigContainRegister" v-else>
					<div class="btitle">创建账户</div>
					<el-form 
						:label-position="labelPosition"
						ref="ruleFormRefRegister"
						:model="registerForm" 
						:rules="rules" 
						style="max-width: 250px" 
						status-icon
					>
							<el-form-item label="邮箱：" prop="email">
							<el-input 
								v-model="registerForm.email" 
								placeholder = "电子邮箱"
							/>
						</el-form-item>
						<el-form-item prop="password" label="密码">
									<el-input 
									v-model="registerForm.password" 
									type = "password"
									placeholder = "8至16位，大小写字母+数字"
									autocomplete="off"
									prop="password"
									/>
								</el-form-item>
						<el-form-item prop="re_password">
								<el-input 
								v-model="registerForm.re_password" 
								placeholder = "请再输入一次" 
								type = "password" 
								autocomplete="off"
								prop="re_password"
								/>
								</el-form-item>		
							<!-- <div style = "margin : 10px" /> -->
							<el-row>
								<el-col :span = "12">
									<el-form-item label="验证码信息：" prop="verification_code">
										<el-input 
											v-model="registerForm.verification_code" 
											placeholder = "数字验证码"
										/>
									</el-form-item>
								</el-col>
								<el-col :span="1"></el-col>
								<el-col :span = "11">
									<el-button type="primary" @click="getVerificationCode">获取验证码</el-button>
								</el-col>
							</el-row>
					</el-form>
					<el-button 
						type = "primary" 
						size ="large" 
						round 
						@click="nextStep()"
					>下一步</el-button>
				</div>
			</div>
			<div class="small-box" :class="{active:isLogin}">
				<div class="small-contain" key="smallContainRegister" v-if="isLogin">
					<div class="stitle">你好，朋友!</div>
					<p class="scontent">开始注册，和我们一起旅行</p>
					<button class="sbutton" @click="changeType">注册</button>
				</div>
				<div class="small-contain" key="smallContainLogin" v-else>
					<div class="stitle">欢迎回来!</div>
					<p class="scontent">请登录你的账户</p>
					<button class="sbutton" @click="changeType">登录</button>
				</div>
			</div>
		</div>
	</div>
</template>

<script lang="ts">
	import { reactive, ref } from 'vue'
	import type { FormInstance } from 'element-plus'
	export default{
		name:'login-register',
		data(){
			const ruleFormRefRegister = ref<FormInstance>()
			const loginForm = reactive({
				email : '',
				password : ''
			})
			const registerForm = reactive({
				email : '',
				password : '',
				re_password : '',
				verification_code : ''
			})
			const validateEmail = (rule : any,value : any,callback : any)=>{
				var reg  = /^[0-9a-zA-Z_.-]+[@][0-9a-zA-Z_.-]+([.][a-zA-Z]+){1,3}$/;
				if (value == '') {
    				callback(new Error('该栏不能为空'));
  				}
				else if(reg.test(value)){
					callback();
				}
				else{
					callback(new Error('邮箱格式错误'));
				}
			}
			const validatePassword = (rule : any,value : any,callback : any)=>{
				let reg = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,16}$/;
				if (value == '') {
    				callback(new Error('该栏不能为空'));
  				}
				else if(reg.test(value)){
					if(registerForm.re_password !== ''){
						if(!ruleFormRefRegister.value) callback();
						ruleFormRefRegister.value?.validateField('re_password',()=>null)
					}
					callback();
				}
				else{
					callback(new Error('密码非法，请使用新的密码'));
				}
			}
			const matchPassword = (rule :any,value:any,callback : any)=>{
				if (value == '') {
    				callback(new Error('该栏不能为空'));
  				}
				else if(value == registerForm.password){
					callback();
				}
				else{
					callback(new Error('两次输入密码不一致'));
				}
			}
			const validateVerification = (rule :any,value:any,callback : any)=>{//验证码在前端验证
				if (value == '') {
    				callback(new Error('该栏不能为空'));
  				}
				else if(value != realVerficationCode){
					callback(new Error('验证码不正确'));
				}
				else callback();
			}
			const rules = reactive({
  				email: [{ validator: validateEmail, trigger: 'blur' }],
  				password: [{ validator: validatePassword, trigger: 'blur' }],
  				re_password: [{ validator: matchPassword, trigger: 'blur' }],
				verification_code : [{validator:validateVerification,trigger : 'blur'}]
			})
			const picURL = "src/assets/Logo.png"
			const labelPosition = ref('top')
			const picformat = ref('fit')
			var realVerficationCode = ''
			const subscribeList = reactive([//用户选择的语言列表
			])
			const languageList = reactive([//语言列表
			])
			return {
				isLogin:false,
				realVerficationCode,
				ruleFormRefRegister,
				existed: false,
				picURL,
				picformat,
				loginForm,
				registerForm,
				labelPosition,
				subscribeList,
				languageList,
				rules
			}
		},
		methods:{
			changeType() {//登录与注册切换
				this.isLogin = !this.isLogin
				this.loginForm.email = ''
				this.loginForm.password = ''
				this.registerForm.email = '',
				this.registerForm.password = '',
				this.registerForm.re_password = '',
				this.registerForm.verification_code = ''
			},
			login() {//登陆
				const self = this;
				if (self.loginForm.email != "" && self.loginForm.password != "") {
					self.$axios({
						method:'post',
						url: '/users/login',
						data: {
							email: self.loginForm.email,
							password: self.loginForm.password
						}
					})
					.then( res => {
						if(res.data.data==1){
							self.$notify.success({
								messgage :'登陆成功，即将跳转首页'
							});
							self.$router.push({name:'Home'})
							//登陆成功则跳转首页
						}
						else{
							self.$notify.error({
          						message: '邮箱或密码输入错误'
       						 });
						}
					})
					.catch( err => {
						console.log(err);
					})
				} else{
					this.$notify.warning({
          						message: '邮箱或密码均不能为空'
       						 });
				}
			},
			getVerificationCode(){//从后端获取验证码
				const self = this;
				self.$axios({
					method : 'post',
					url : '',
					data : {
						email : self.registerForm.email
					}
				})
				.then(res => {
					self.$notify.success({
						messgage :'验证码获取成功'
					});
					self.realVerficationCode = res.data.data.verificationCode;
				})
				.catch(err =>{
					self.$notify.error({
						messgage :'系统繁忙，稍后再试'
					});
					console.log(err);
				})	
			},
			nextStep() {//下一步选择专业和订阅语言
				var f = 0;
				const self = this;
				var regEmail  = /^[0-9a-zA-Z_.-]+[@][0-9a-zA-Z_.-]+([.][a-zA-Z]+){1,3}$/;
				let regPassword = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,16}$/;
				if (this.registerForm.email == ''||!regEmail.test(this.registerForm.email)) {
    				f = 1;
  				}
				else if(this.registerForm.password == ''||!regPassword.test(this.registerForm.password)){
					f = 1;
				}
				else if(this.registerForm.re_password == ''||this.registerForm.password != this.registerForm.re_password){
					f = 1;
				}	
				if(f){
					self.$notify.error({
						messgage :'请正确填写表单信息'
					});
				}
				else{
					self.$axios({
					method : 'post',
					url : '',
					data: {
							email: self.registerForm.email,
						}
				})
				.then(res => {
					if(res.data.data.existed==1){
						self.$notify.success({
							messgage :'请进一步完善您的信息'
						});
					}
					else{
						self.$notify.error({
							messgage :'邮箱已经=存在'
						});
					}
				})
				.catch(err =>{
					self.$notify.error({
						messgage :'系统繁忙，稍后再试'
					});
					console.log(err);
				})	
				}
			},
		}
	}
</script>

<style scoped="scoped">	
	.login-register{
		width: 100vw;
		height: 100vh;
		box-sizing: border-box;
		background: #92a6be;
	}
	.contain{
		width: 40%;
		height: 55%;
		position: relative;
		top: 50%;
		left: 50%;
		transform: translate(-50%,-50%);
		background-color: rgb(255, 255, 255);
		border-radius: 20px;
		box-shadow: 0 0 3px #f0f0f0,
					0 0 6px #f0f0f0;
	}
	.big-box{
		width: 60%;
		height: 100%;
		position: absolute;
		top: 0;
		left: 40%;
		transform: translateX(0%);
		transition: all 1s;
	}
	.big-contain{
		width: 100%;
		height: 100%;
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
	}
	.btitle{
		font-size: 1.5em;
		font-weight: bold;
		color: rgb(85, 85, 255);
	}
	.bform{
		width: 100%;
		height: 40%;
		padding: 2em 0;
		display: flex;
		flex-direction: column;
		justify-content: space-around;
		align-items: center;
	}
	.bform .errTips{
		display: block;
		width: 50%;
		text-align: left;
		color: red;
		font-size: 0.7em;
		margin-left: 1em;
	}
	.bform input{
		width: 50%;
		height: 30px;
		border: none;
		outline: none;
		border-radius: 10px;
		padding-left: 2em;
		background-color: #f0f0f0;
	}
	.bbutton{
		width: 20%;
		height: 40px;
		border-radius: 24px;
		border: none;
		outline: none;
		background-color: rgb(57,167,176);
		color: #fff;
		font-size: 0.9em;
		cursor: pointer;
	}
	.small-box{
		width: 40%;
		height: 100%;
		/* background: linear-gradient(135deg,rgb(86, 107, 244),rgb(65, 55, 210)); */
		background: linear-gradient(45deg, rgb(85, 227, 255) 50%, #55f 0);
		position: absolute;
		top: 0;
		left: 0;
		transform: translateX(0%);
		transition: all 1s;
		border-top-left-radius: inherit;
		border-bottom-left-radius: inherit;
	}	
	.small-contain{
		width: 100%;
		height: 100%;
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
	}
	.stitle{
		font-size: 1.5em;
		font-weight: bold;
		color: #fff;
	}
	.scontent{
		font-size: 0.8em;
		color: #fff;
		text-align: center;
		padding: 2em 4em;
		line-height: 1.7em;
	}
	.sbutton{
		width: 60%;
		height: 40px;
		border-radius: 24px;
		border: 1px solid #fff;
		outline: none;
		background-color: transparent;
		color: #fff;
		font-size: 0.9em;
		cursor: pointer;
	}
	
	.big-box.active{
		left: 0;
		transition: all 0.5s;
	}
	.small-box.active{
		left: 100%;
		border-top-left-radius: 0;
		border-bottom-left-radius: 0;
		border-top-right-radius: inherit;
		border-bottom-right-radius: inherit;
		transform: translateX(-100%);
		transition: all 1s;
	}
</style>
