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
			axios.get('http://localhost:8080/api/clients/current/')
				.then(response => {
					this.datos = response.data;
                    this.accounts = this.datos.accounts;
					this.loans = this.datos.loans;
				})
				.catch(error => console.log(error));
		},
		logout(){
			axios.post("/api/logout")
			.then(response => window.location.href = "/web/pages/signon.html" )
		},
		createAccount(){
			Swal.fire({
                title: 'Are you sure you want to create a new account?',
                inputAttributes: {
                    autocapitalize: 'off'
                },
                showCancelButton: true,
                confirmButtonText: 'Sure',
                showLoaderOnConfirm: true,
                preConfirm: () => {
                    return axios.post('/api/clients/current/accounts')
                        .then(response => window.location.href = "/web/pages/accounts.html")
                        .catch(error => {
                            Swal.fire({
                                icon: 'error',
                                text: error.response.data,
                            })
                            console.log(error)
                        })
                },
			})
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
