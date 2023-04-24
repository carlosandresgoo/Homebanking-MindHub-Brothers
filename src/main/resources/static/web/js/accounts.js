const {createApp} = Vue;

createApp({
	data() {
		return {
			datos: [],
			loans: [],
			firstname: '',
			lastName: '',
			email: '',
            accounts:[],
			isAsideInactive: true,
		};
	},
	created() {
		this.loadData();
	},
	methods: {
		loadData() {
			axios
				.get('http://localhost:8080/api/clients/current')
				.then(response => {
					this.datos = response.data;
					console.log(this.datos);
                    this.accounts = this.datos.accounts;
                    console.log(this.accounts);
					this.loans = this.datos.loans;
					console.log(this.loans);
				})
				.catch(error => console.log(error));
		},
		logout(){
			axios.post("/api/logout")
			.then(response => window.location.href = "/web/pages/signon.html" )
		},
		aparecermenu() {
            this.isAsideInactive = !this.isAsideInactive;
        },
		
	},
}).mount('#app');