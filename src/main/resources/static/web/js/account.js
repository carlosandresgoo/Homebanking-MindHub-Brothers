const {createApp} = Vue;

createApp({
	data() {
		return {
			account: [],
			transactions: [],
			type:"",
			description:"",
			isAsideInactive: true,
			id: new URLSearchParams(location.search).get('id'),
		};
	},
	created() {
		this.loadData();
	},
	methods: {
		loadData() {
			axios
				.get('http://localhost:8080/api/clients/current/accounts/' + this.id)
				.then(response => {
					this.account = response.data;
					this.transactions = this.account.transactions;

		})
				.catch(error => console.log(error));
		},
		logout(){
			axios.post("/api/logout")
			.then(response => window.location.href = "/web/pages/signon.html" )

		},
		appearmenu() {
            this.isAsideInactive = !this.isAsideInactive;
        },
		
	},
}).mount('#app');
const btnScrollTop = document.querySelector('#btn-scroll-top');

btnScrollTop.addEventListener('click', function () {
	window.scrollTo({
		top: 0,
		behavior: 'smooth'
	});
});

window.addEventListener('scroll', function () {
	if (window.pageYOffset > 50) {
		btnScrollTop.style.display = 'block';
	} else {
		btnScrollTop.style.display = 'none';
	}
});