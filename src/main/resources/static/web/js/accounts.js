const { createApp } = Vue


createApp({

    data (){
        return{
            datos:[],
            name:"",
            lastName:"",
            email:"",
            number :"",
            balance:"",
            creationDate:"",


        }

    },

    created(){
    this.loadDate()
    },

    methods:{

        loadDate(){
            axios.get('http://localhost:8080/api/clients')
            .then(response => {
            this.datos = response.data;
            console.log(this.dato.accounts);
        })
        .catch (err => console.log(err))
        },
    },

}).mount ('#app')
