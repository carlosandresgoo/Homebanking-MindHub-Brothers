const { createApp } = Vue


createApp({

    data (){
        return{
            datos:[],
            FirstName:"",
            lastName:"",
            email:"",
        }

    },

    created(){
    this.loadDate()
    },

    methods:{

        loadDate(){
            axios.get('http://localhost:8080/api/clients/')
            .then(response => {
            this.datos = response.data;
            console.log(this.datos);
        })
        .catch (err => console.log(err))
        },

        // addClients(){
        //     axios.post('http://localhost:8080/api/clients', {
        //         name : this.name,
        //         lastName : this.lastName,
        //         email : this.email,
        //     })
        //     .then(response => {
        //         this.datos.push(response.data);
        //         this.name="";
        //         this.lastName="";
        //         this.email="";
        //     })
        //     .catch(err => console.log(err))     
        // },

        // deleteClients(){
        //     axios.delete('http://localhost:8080/clients')
        //     .then(response =>{
        //         this.loadDate();
        //     })
        //     .catch(err => console.log(err))
        // }
        
    },

}).mount ('#app')


// loading//
const loadingContainer = document.getElementById("loading-container");
function showLoading() {
	loadingContainer.style.display = "flex";
}
function hideLoading() {
	loadingContainer.style.display = "none";
}
showLoading();
window.addEventListener("load", () => {
	hideLoading();
});
