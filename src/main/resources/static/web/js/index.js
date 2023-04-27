
const { createApp } = Vue;

createApp({
	data() {
		return {
			datos: [],
			cards: [],
			debit: [],
			credit: [],
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
					this.card = this.datos.cards;
					console.log(this.card);
					this.credit = this.datos.cards.filter(card => card.type == "CREDIT");
					console.log(this.credit)
					this.debit = this.datos.cards.filter(card => card.type == "DEBIT");
					console.log(this.debit)
				})
				.catch(error => console.log(error));
		},
		appearmenu() {
			this.isAsideInactive = !this.isAsideInactive;
		},
	},
}).mount('#app');
//boton
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



